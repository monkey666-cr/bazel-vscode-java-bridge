# WeavingHook NPE Fix — JDTUtils.searchDecompiledSources

> Implemented: 2026-05-20

---

## 1. Problem

JDT.LS has an unfixed bug ([eclipse-jdtls#3083](https://github.com/eclipse-jdtls/eclipse.jdt.ls/issues/3083)) in `JDTUtils.searchDecompiledSources()`. The `MethodInvocation` branch is missing a `finder.initialize()` call:

```java
// JDTUtils.java (JDT.LS 1.58.0), around line 1830
} else if (node instanceof MethodInvocation mi) {
    SimpleName name = mi.getName();
    // BUG: missing finder.initialize(unit, name);
}
OccurrenceLocation[] occurrences = finder.getOccurrences();
for (OccurrenceLocation occurrence : occurrences) {  // NPE: occurrences is null
```

This causes a `NullPointerException` that crashes "Find References" and "Go to Implementation" requests entirely when the search hits a classpath JAR without source attachment.

**Affected callers:**
- `ReferencesHandler$1.acceptSearchMatch()` — "Find References"
- `NavigateToDefinitionHandler` — "Go to Definition"

Both enter the `searchDecompiledSources` code path when `classFile.getSourceRange() == null` (no source attachment).

## 2. Solution Overview

We use the OSGi standard **WeavingHook** API to patch the bytecode of classes that **call** `searchDecompiledSources`, wrapping the specific call site in a `try-catch(NullPointerException)` that returns `Collections.emptyList()` on catch.

```
┌──────────── Before (crashes) ──────────────────────┐
│                                                     │
│  ReferencesHandler$1.acceptSearchMatch()            │
│    │                                                │
│    └─ JDTUtils.searchDecompiledSources(...)         │
│         └─ NPE thrown → entire request fails        │
│                                                     │
├──────────── After (graceful degradation) ───────────┤
│                                                     │
│  ReferencesHandler$1.acceptSearchMatch()            │
│    │                                                │
│    ├─ try {                                         │
│    │    JDTUtils.searchDecompiledSources(...)        │
│    │ } catch (NullPointerException) {               │
│    │    return Collections.emptyList();              │
│    │ }                                              │
│    │                                                │
│    └─ Request completes with partial results        │
│                                                     │
└─────────────────────────────────────────────────────┘
```

## 3. Why Patch Callers, Not JDTUtils Itself

The initial approach was to patch `JDTUtils.searchDecompiledSources()` directly — wrapping the entire method body in try-catch. This failed because of class loading timing:

```
Timeline:
  19:07:53  JavaLanguageServerPlugin started → JDTUtils class loaded
  19:07:58  Our bundle starts → WeavingHook registered (too late for JDTUtils)
  19:09:39  NPE fires in searchDecompiledSources (unpatched)
```

**WeavingHook only intercepts first-time class loading** (`defineClass`). Since `JDTUtils` is a core utility class loaded during JDT.LS initialization (before our bundle is even installed), the hook never fires for it.

**Handler classes are lazily loaded** — `ReferencesHandler`, `NavigateToDefinitionHandler`, and their inner classes are only loaded when the user first triggers the corresponding LSP request, well after our WeavingHook is registered.

## 4. Implementation Details

### 4.1 Files Changed

| File | Change |
|------|--------|
| `java-bridge/pom.xml` | Added `org.ow2.asm:asm:9.7` (compile) and `org.osgi:osgi.core:8.0.0` (provided) |
| `java-bridge/bnd.bnd` | Added `org.osgi.framework.hooks.weaving` to Import-Package, `Private-Package` for ASM embedding |
| `java-bridge/src/main/java/com/bazel/jdt/JDTUtilsPatcher.java` | **New** — WeavingHook implementation |
| `java-bridge/src/main/java/com/bazel/jdt/BazelActivator.java` | Register/unregister WeavingHook service |
| `java-bridge/src/test/java/com/bazel/jdt/JDTUtilsPatcherTest.java` | **New** — 4 unit tests |

### 4.2 JDTUtilsPatcher Architecture

```
┌────────────────── weave(WovenClass) ──────────────────────┐
│                                                            │
│  1. Bundle filter                                          │
│     └─ wovenClass.bundle != "org.eclipse.jdt.ls.core"?     │
│        → skip (most classes never reach step 2)            │
│                                                            │
│  2. Pre-scan (read-only, no COMPUTE_FRAMES)                │
│     └─ containsTargetCallSite(bytes):                      │
│        ClassReader + bare MethodVisitor scans for           │
│        INVOKESTATIC JDTUtils.searchDecompiledSources        │
│        using SKIP_DEBUG | SKIP_FRAMES flags                 │
│        → No call site found? skip                          │
│                                                            │
│  3. Patch (only reached for classes with the call site)    │
│     └─ patchCallerClass(bytes):                            │
│        ClassReader → ClassVisitor → CallSiteWrappingVisitor │
│        → SafeClassWriter (COMPUTE_FRAMES + COMPUTE_MAXS)   │
│                                                            │
│        CallSiteWrappingVisitor.visitMethodInsn():           │
│          When seeing INVOKESTATIC to searchDecompiledSources│
│          → Emit: try { originalCall } catch (NPE) {        │
│                    return Collections.emptyList();          │
│                  }                                         │
│                                                            │
│  4. Apply                                                  │
│     └─ wovenClass.setBytes(patchedBytes)                   │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

### 4.3 The Pre-scan Optimization

Without pre-scanning, `patchCallerClass()` runs `ClassWriter(COMPUTE_FRAMES)` on every class from the JDT.LS bundle. `COMPUTE_FRAMES` calls `getCommonSuperClass()` internally, which uses `Class.forName()` — this fails in OSGi when the target class references types not visible to our bundle's classloader (e.g., `org.gradle.tooling.model.GradleProject`, `org.eclipse.lsp4j.CompletionItem`).

The pre-scan uses a bare `ClassVisitor` with no `ClassWriter`, so no frame computation occurs. Only classes confirmed to contain the call site proceed to the full transformation.

### 4.4 SafeClassWriter

Even with pre-scanning, the classes that DO contain the call site may reference types outside our bundle's visibility. `SafeClassWriter` overrides `getCommonSuperClass()` to fall back to `"java/lang/Object"` when type resolution fails, instead of throwing `TypeNotPresentException`.

### 4.5 BazelActivator Integration

```java
// In start():
weavingHookRegistration = context.registerService(
    WeavingHook.class, new JDTUtilsPatcher(), null);

// In stop():
if (weavingHookRegistration != null) {
    weavingHookRegistration.unregister();
    weavingHookRegistration = null;
}
```

The hook is registered during `BazelActivator.start()`, which executes during `BundleUtils.loadBundles()`. At this point, handler classes have not yet been loaded (they are loaded on first LSP request), so the WeavingHook fires in time.

## 5. Degradation Strategy

```
┌────────────────── Degradation Layers ─────────────────────┐
│                                                            │
│  Layer 1: WeavingHook registration                         │
│    ├─ Success → call site patched when handler loads       │
│    └─ Failure → bundle still works, NPE persists (current │
│                 behavior, no regression)                    │
│                                                            │
│  Layer 2: Pre-scan + patch                                 │
│    ├─ Call site found → patch applied                      │
│    └─ Not found → JDT.LS version changed method name,     │
│                   no modification (silent skip)             │
│                                                            │
│  Layer 3: Runtime catch                                    │
│    ├─ NPE thrown → caught, returns emptyList()             │
│    │   "Find References" shows partial results             │
│    └─ No NPE → method returns normally (no overhead)       │
│                                                            │
│  If upstream fixes the bug: patch becomes a no-op          │
│  (try-catch still safe, just never triggers)               │
│                                                            │
└────────────────────────────────────────────────────────────┘
```

## 6. Version Compatibility

The patch is designed to be robust across JDT.LS versions:

- **Method matching**: Only matches by owner class internal name (`org/eclipse/jdt/ls/core/internal/JDTUtils`) and method name (`searchDecompiledSources`). Does **not** match by descriptor, so signature changes (new parameters, different return type) are tolerated.
- **Call site wrapping**: Wraps the specific `INVOKESTATIC` instruction, not the entire method. Works regardless of the caller's method structure.
- **Silent skip**: If `searchDecompiledSources` is renamed or removed in a future version, the pre-scan finds no call sites and the hook is a no-op.
- **Upstream fix**: If the NPE bug is fixed upstream, the try-catch still compiles and loads correctly — it simply never catches anything.

## 7. Testing

Four unit tests in `JDTUtilsPatcherTest.java`:

| Test | Verifies |
|------|----------|
| `patchCallerClass_wrapsCallSite` | ASM transformation produces different bytecode for a class calling `searchDecompiledSources` |
| `patchCallerClass_skipsClassWithNoCallSite` | Returns null (no modification) for unrelated classes |
| `patchCallerClass_skipsWhenOwnerDiffers` | Does not patch calls to methods with the same name on different classes |
| `patchedCallSite_returnsEmptyListInsteadOfNPE` | End-to-end: loads a fake JDTUtils that throws NPE, patches the caller, invokes it, and verifies `Collections.emptyList()` is returned |

Run tests:
```bash
cd java-bridge && mvn test -Dtest=JDTUtilsPatcherTest
```

## 8. Approaches Considered and Rejected

| Approach | Why Rejected |
|----------|-------------|
| **Stub source JAR generation** | Persistent edge cases with inner classes, anonymous classes, synthetic classes. Reverted after multiple iterations. |
| **OSGi Fragment** | Equinox `ClasspathManager` searches host classpath entries before fragment entries — fragments cannot replace existing host classes. |
| **Patch JDTUtils directly** | `JDTUtils` is loaded during JDT.LS initialization, before our bundle starts. WeavingHook never fires for already-loaded classes. |
| **Equinox ClassLoaderHook** | Internal API, requires framework extension configuration (`hookconfigurators.properties`), cannot be registered from a normal bundle. |
| **Upstream PR** | Outside project control, uncertain timeline. |
| **Disable `includeDecompiledSources`** | Globally disables decompiled source browsing — too large a UX degradation. |
