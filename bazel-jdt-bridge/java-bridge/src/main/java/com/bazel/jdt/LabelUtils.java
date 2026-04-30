package com.bazel.jdt;

public final class LabelUtils {

    private LabelUtils() {}

    public static String extractPackageName(String targetLabel) {
        int colonIndex = targetLabel.lastIndexOf(':');
        if (colonIndex > 2) {
            return targetLabel.substring(2, colonIndex);
        }
        return targetLabel.substring(2);
    }
}
