package com.bazel.jdt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Validates that the OSGi bundle manifest (bnd.bnd) declares all required
 * bundles that the Java source code imports from. Catches the exact class
 * of bug where a dependency is in pom.xml but missing from Require-Bundle,
 * causing NoClassDefFoundError at OSGi runtime while Maven tests pass.
 */
public class OsgiBundleConsistencyTest {

    private static final String PROJECT_DIR = System.getProperty("project.dir",
            Paths.get("").toAbsolutePath().toString());
    private static final Path BND_FILE = Paths.get(PROJECT_DIR, "bnd.bnd");
    private static final Path SRC_DIR = Paths.get(PROJECT_DIR,
            "src", "main", "java", "com", "bazel", "jdt");

    private static Set<String> declaredBundles;
    private static Set<String> importedEclipsePackages;

    @BeforeClass
    public static void parseBndAndSource() throws Exception {
        declaredBundles = parseRequireBundle(BND_FILE);
        importedEclipsePackages = scanEclipseImports(SRC_DIR);
    }

    @Test
    public void bndFileExists() {
        assertTrue("bnd.bnd must exist at " + BND_FILE, Files.exists(BND_FILE));
    }

    @Test
    public void requireBundleIncludesJdtLaunching() {
        assertTrue(
            "Require-Bundle must include org.eclipse.jdt.launching "
            + "(BazelProjectImporter uses JavaRuntime). Declared: " + declaredBundles,
            declaredBundles.contains("org.eclipse.jdt.launching"));
    }

    @Test
    public void requireBundleIncludesJdtCore() {
        assertTrue(
            "Require-Bundle must include org.eclipse.jdt.core. Declared: " + declaredBundles,
            declaredBundles.contains("org.eclipse.jdt.core"));
    }

    @Test
    public void requireBundleIncludesJdtLsCore() {
        assertTrue(
            "Require-Bundle must include org.eclipse.jdt.ls.core. Declared: " + declaredBundles,
            declaredBundles.contains("org.eclipse.jdt.ls.core"));
    }

    @Test
    public void requireBundleIncludesCoreResources() {
        assertTrue(
            "Require-Bundle must include org.eclipse.core.resources. Declared: " + declaredBundles,
            declaredBundles.contains("org.eclipse.core.resources"));
    }

    @Test
    public void allEclipseImportsCoveredByRequireBundle() {
        Set<String> uncovered = new HashSet<>();
        for (String pkg : importedEclipsePackages) {
            String bundle = packageToBundle(pkg);
            if (bundle != null && !declaredBundles.contains(bundle)) {
                uncovered.add(bundle + " (from import " + pkg + ")");
            }
        }
        assertTrue(
            "Require-Bundle must cover all Eclipse imports. Missing: " + uncovered,
            uncovered.isEmpty());
    }

    private static Set<String> parseRequireBundle(Path bndFile) throws Exception {
        Set<String> bundles = new HashSet<>();
        Pattern pattern = Pattern.compile("Require-Bundle:\\s*(.+)");
        try (BufferedReader reader = new BufferedReader(new FileReader(bndFile.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    String[] parts = m.group(1).split(",");
                    for (String part : parts) {
                        bundles.add(part.trim().split(";")[0].trim());
                    }
                }
            }
        }
        return bundles;
    }

    private static Set<String> scanEclipseImports(Path srcDir) throws Exception {
        Set<String> packages = new HashSet<>();
        Pattern importPattern = Pattern.compile("^import\\s+(org\\.eclipse\\.[\\w.]+)\\.");
        Files.walk(srcDir)
            .filter(p -> p.toString().endsWith(".java"))
            .forEach(p -> {
                try {
                    for (String line : Files.readAllLines(p)) {
                        Matcher m = importPattern.matcher(line);
                        if (m.find()) {
                            packages.add(m.group(1));
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        return packages;
    }

    private static String packageToBundle(String pkg) {
        if (pkg.startsWith("org.eclipse.jdt.ls.core")) return "org.eclipse.jdt.ls.core";
        if (pkg.startsWith("org.eclipse.jdt.launching")) return "org.eclipse.jdt.launching";
        if (pkg.startsWith("org.eclipse.jdt.core")) return "org.eclipse.jdt.core";
        if (pkg.startsWith("org.eclipse.core.resources")) return "org.eclipse.core.resources";
        return null;
    }
}
