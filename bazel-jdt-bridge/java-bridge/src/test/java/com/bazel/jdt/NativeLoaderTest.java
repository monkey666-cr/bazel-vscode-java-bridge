package com.bazel.jdt;

import static org.junit.Assert.*;

import org.junit.Test;

public class NativeLoaderTest {

    @Test
    public void detectPlatformReturnsNonNull() {
        assertNotNull(PlatformDetector.detectPlatform());
    }

    @Test
    public void detectPlatformMatchesKnownOs() {
        String platform = PlatformDetector.detectPlatform();
        assertTrue("Platform should start with linux, darwin, or windows, got: " + platform,
                platform.startsWith("linux-") || platform.startsWith("darwin-") || platform.startsWith("windows-"));
    }

    @Test
    public void detectPlatformContainsDash() {
        String platform = PlatformDetector.detectPlatform();
        assertTrue("Platform format should be os-arch, got: " + platform,
                platform.contains("-"));
    }

    @Test
    public void libraryFileNameLinuxX86_64() {
        assertEquals("libbazel_jdt_core.so", PlatformDetector.getLibraryFileName("linux-x86_64"));
    }

    @Test
    public void libraryFileNameLinuxAarch64() {
        assertEquals("libbazel_jdt_core.so", PlatformDetector.getLibraryFileName("linux-aarch64"));
    }

    @Test
    public void libraryFileNameDarwinX86_64() {
        assertEquals("libbazel_jdt_core.dylib", PlatformDetector.getLibraryFileName("darwin-x86_64"));
    }

    @Test
    public void libraryFileNameDarwinAarch64() {
        assertEquals("libbazel_jdt_core.dylib", PlatformDetector.getLibraryFileName("darwin-aarch64"));
    }

    @Test
    public void libraryFileNameWindowsX86_64() {
        assertEquals("bazel_jdt_core.dll", PlatformDetector.getLibraryFileName("windows-x86_64"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void libraryFileNameUnknownPlatformThrows() {
        PlatformDetector.getLibraryFileName("unknown-arch");
    }
}
