package com.example.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

/**
 * Standard JUnit4 test (no PowerMock). Verifies that the PowerMock guard
 * does not regress normal test behavior.
 */
public class ConfigServiceTest {

    @Test
    public void testGetMaxRetriesDefault() {
        assertEquals(3, ConfigService.getMaxRetries());
    }

    @Test
    public void testIsDebugModeDefault() {
        assertFalse(ConfigService.isDebugMode());
    }
}
