package com.bazel.jdt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Parses IntelliJ-compatible .bazelproject files.
 * Supported sections: directories, derive_targets_from_directories, targets.
 */
public final class BazelProjectView {
    private static final String BAZELPROJECT_FILE = ".bazelproject";

    private final List<String> directories = new ArrayList<>();
    private final List<String> targets = new ArrayList<>();
    private boolean deriveTargetsFromDirectories = true;

    private BazelProjectView() {}

    public static BazelProjectView parse(File workspaceRoot) {
        File projectViewFile = new File(workspaceRoot, BAZELPROJECT_FILE);
        if (!projectViewFile.exists()) {
            return null;
        }

        BazelProjectView view = new BazelProjectView();
        String currentSection = null;

        try (BufferedReader reader = new BufferedReader(new FileReader(projectViewFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                if (trimmed.endsWith(":")) {
                    currentSection = trimmed.substring(0, trimmed.length() - 1).trim().toLowerCase();
                    continue;
                }

                if (currentSection == null) {
                    continue;
                }

                switch (currentSection) {
                    case "directories":
                        view.directories.add(trimmed);
                        break;
                    case "derive_targets_from_directories":
                        view.deriveTargetsFromDirectories = "true".equalsIgnoreCase(trimmed);
                        break;
                    case "targets":
                        view.targets.add(trimmed);
                        break;
                    default:
                        break;
                }
            }
        } catch (IOException e) {
            System.err.println("[bazel-jdt] Failed to parse .bazelproject: " + e.getMessage());
        }

        return view;
    }

    public List<String> getScopePatterns() {
        List<String> patterns = new ArrayList<>();

        if (deriveTargetsFromDirectories) {
            for (String dir : directories) {
                if (dir.startsWith("-")) {
                    String path = dir.substring(1);
                    if (".".equals(path)) {
                        patterns.add("-//...:*");
                    } else {
                        patterns.add("-//" + path + "/...:*");
                    }
                } else {
                    if (".".equals(dir)) {
                        patterns.add("//...:*");
                    } else {
                        patterns.add("//" + dir + "/...:*");
                    }
                }
            }
        }

        patterns.addAll(targets);
        return patterns;
    }

    public List<String> getDirectories() {
        return Collections.unmodifiableList(directories);
    }

    public List<String> getTargets() {
        return Collections.unmodifiableList(targets);
    }

    public boolean isDeriveTargetsFromDirectories() {
        return deriveTargetsFromDirectories;
    }

    public boolean hasScope() {
        return !directories.isEmpty() || !targets.isEmpty();
    }
}
