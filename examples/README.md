# Example Bazel Java Projects

Test projects for validating the Bazel JDT Bridge VS Code extension.

## Projects

| Project | Description | External Deps | Rules Used |
|---------|-------------|---------------|------------|
| `simple-java-project` | Minimal self-contained | None | `java_library`, `java_binary`, `java_test` |
| `maven-deps-project` | Guava + JUnit via `maven_install` | Guava, JUnit, Hamcrest | `java_library`, `java_binary`, `java_test` |
| `multi-module-project` | Layered architecture with exports | Guava, JUnit, Hamcrest | `java_library`, `java_binary`, `java_test` + `exports`, `resources` |

## Usage

### Build & Test

```bash
# Simple project (no network required)
cd simple-java-project
bazel build //...
bazel test //...
bazel run //app:app

# Maven deps project (downloads dependencies on first build)
cd maven-deps-project
bazel build //...
bazel test //...
bazel run //app:application

# Multi-module project
cd multi-module-project
bazel build //...
bazel test //...
bazel run //server:server
```

### Testing with Bazel JDT Bridge

1. Build and install the VS Code extension:
   ```bash
   cd ../../bazel-jdt-bridge
   ./scripts/package-extension.sh
   code --install-extension build/bazel-jdt-bridge-0.1.0.vsix
   ```

2. Open any example project in VS Code:
   ```bash
   code ../examples/simple-java-project
   ```

3. The extension activates when:
   - A `WORKSPACE` or `WORKSPACE.bazel` file is detected at the root
   - A Java file is opened (`onLanguage:java`)

4. Verify functionality:
   - `Bazel: Import Project` — discovers all Java targets
   - Open a `.java` file — check for code completion, go-to-definition
   - Edit a `BUILD.bazel` file, save — triggers auto-sync (if `syncOnSave` enabled)

## What Each Project Tests

### simple-java-project
- Basic workspace detection (`WORKSPACE` file)
- `java_library` + `java_binary` cross-package dependency (`//greeter:greeter`)
- `java_test` without external test framework
- All concrete `srcs`/`deps` (exercises fast path)

### maven-deps-project
- `maven_install` with `rules_jvm_external`
- External Maven artifact resolution (`@maven//:...` labels)
- JUnit 4 test runner integration
- Multi-layer dependency chain (utils → service → app)
- Transitive dependency resolution through Guava

### multi-module-project
- Interface/implementation split (`api` → `core`)
- `exports` attribute (core exports api, server gets api transitively)
- `resources` attribute (`config.properties`)
- Multiple `java_test` targets in one package
- Complex dependency graph exercising BFS traversal
