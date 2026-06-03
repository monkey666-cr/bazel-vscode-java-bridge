package com.bazel.jdt;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class InternalConfig {
    public static final String BASE_DIR;
    public static final String CONFIG_FILE;
    public static final String PROJECTS_DIR;
    public static final String ASPECTS_DIR;

    static {
        String baseDir = ".bazel-jdt";
        String configFile = ".bazelproject";
        String projectsDir = "projects";
        String aspectsDir = "aspects";
        try (InputStream in = InternalConfig.class.getClassLoader().getResourceAsStream("config.json")) {
            if (in != null) {
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                baseDir = extractJsonString(json, "baseDir", baseDir);
                configFile = extractJsonString(json, "configFile", configFile);
                projectsDir = extractJsonString(json, "projectsDir", projectsDir);
                aspectsDir = extractJsonString(json, "aspectsDir", aspectsDir);
            }
        } catch (Exception e) {
            System.err.println("[bazel-jdt] Failed to load config.json, using defaults: " + e.getMessage());
        }
        BASE_DIR = baseDir;
        CONFIG_FILE = configFile;
        PROJECTS_DIR = projectsDir;
        ASPECTS_DIR = aspectsDir;
    }

    private InternalConfig() {}

    /** Returns `<baseDir>/<configFile>` relative path. */
    public static String bazelprojectRelPath() {
        return BASE_DIR + "/" + CONFIG_FILE;
    }

    /** Returns `<baseDir>/<projectsDir>` relative path. */
    public static String projectsDirRelPath() {
        return BASE_DIR + "/" + PROJECTS_DIR;
    }

    private static String extractJsonString(String json, String key, String defaultValue) {
        String needle = "\"" + key + "\"";
        int keyIdx = json.indexOf(needle);
        if (keyIdx < 0) return defaultValue;
        int colon = json.indexOf(':', keyIdx + needle.length());
        if (colon < 0) return defaultValue;
        int openQuote = json.indexOf('"', colon + 1);
        if (openQuote < 0) return defaultValue;
        int closeQuote = json.indexOf('"', openQuote + 1);
        if (closeQuote < 0) return defaultValue;
        return json.substring(openQuote + 1, closeQuote);
    }
}
