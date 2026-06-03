package com.bazel.jdt;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

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
                JSONObject obj = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                baseDir = obj.optString("baseDir", baseDir);
                configFile = obj.optString("configFile", configFile);
                projectsDir = obj.optString("projectsDir", projectsDir);
                aspectsDir = obj.optString("aspectsDir", aspectsDir);
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

    public static String bazelprojectRelPath() {
        return BASE_DIR + "/" + CONFIG_FILE;
    }

    public static String projectsDirRelPath() {
        return BASE_DIR + "/" + PROJECTS_DIR;
    }
}
