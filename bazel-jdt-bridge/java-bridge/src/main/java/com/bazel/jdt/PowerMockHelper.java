package com.bazel.jdt;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PowerMockHelper {

    private static final Logger LOG = Logger.getLogger(PowerMockHelper.class.getName());

    private static final ConcurrentHashMap<String, Boolean> cache = new ConcurrentHashMap<>();

    private static final String[] POWERMOCK_RUNNER_NAMES = {
        "PowerMockRunner",
        "org.powermock.modules.junit4.PowerMockRunner",
        "PowerMockJUnit44Runner",
        "org.powermock.modules.junit4.legacy.PowerMockJUnit44Runner"
    };

    private PowerMockHelper() {}

    public static boolean isPowerMockRunner(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isEmpty()) {
            return false;
        }
        try {
            return cache.computeIfAbsent(qualifiedName, PowerMockHelper::detectPowerMockRunner);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Error detecting PowerMock for " + qualifiedName, e);
            return false;
        }
    }

    static boolean detectPowerMockRunner(String qualifiedName) {
        Boolean result = isPowerMockRunnerViaIType(qualifiedName);
        if (result != null) {
            LOG.fine("PowerMock detection via JDT API for " + qualifiedName + ": " + result);
            return result;
        }
        boolean heuristic = isPowerMockByClassName(qualifiedName);
        LOG.fine("PowerMock detection via heuristic for " + qualifiedName + ": " + heuristic);
        return heuristic;
    }

    static Boolean isPowerMockRunnerViaIType(String qualifiedName) {
        try {
            org.eclipse.jdt.core.IJavaModel model = org.eclipse.jdt.core.JavaCore.create(
                org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot());
            org.eclipse.jdt.core.IJavaProject[] projects = model.getJavaProjects();

            for (org.eclipse.jdt.core.IJavaProject project : projects) {
                org.eclipse.jdt.core.IType type = project.findType(qualifiedName);
                if (type == null) continue;

                for (org.eclipse.jdt.core.IAnnotation annotation : type.getAnnotations()) {
                    String name = annotation.getElementName();
                    if (!"RunWith".equals(name) && !"org.junit.runner.RunWith".equals(name)) {
                        continue;
                    }
                    for (org.eclipse.jdt.core.IMemberValuePair pair : annotation.getMemberValuePairs()) {
                        if ("value".equals(pair.getMemberName())) {
                            String value = String.valueOf(pair.getValue());
                            for (String runner : POWERMOCK_RUNNER_NAMES) {
                                if (value.contains(runner)) {
                                    return true;
                                }
                            }
                        }
                    }
                    return false;
                }
                return false;
            }
        } catch (Exception e) {
            LOG.log(Level.FINE, "JDT lookup failed for " + qualifiedName, e);
        }
        return null;
    }

    static boolean isPowerMockByClassName(String qualifiedName) {
        String simpleName = qualifiedName.contains(".")
            ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1)
            : qualifiedName;
        return simpleName.contains("PowerMock");
    }

    static void clearCache() {
        cache.clear();
    }

    static int cacheSize() {
        return cache.size();
    }

    static Boolean getCachedResult(String qualifiedName) {
        return cache.get(qualifiedName);
    }
}
