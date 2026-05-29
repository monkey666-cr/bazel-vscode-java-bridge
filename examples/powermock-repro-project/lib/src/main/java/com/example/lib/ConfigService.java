package com.example.lib;

public class ConfigService {

    public static String getEnvironment() {
        return System.getenv("APP_ENV");
    }

    public static int getMaxRetries() {
        String val = System.getenv("MAX_RETRIES");
        return val != null ? Integer.parseInt(val) : 3;
    }

    public static boolean isDebugMode() {
        return "true".equalsIgnoreCase(System.getenv("DEBUG"));
    }
}
