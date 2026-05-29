package com.example.lib;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Reproduces the PowerMock test tree disappearance issue.
 *
 * Without the PowerMock bytecode guard fix, running any single test method
 * (e.g., testGetEnvironment) causes the other two methods to vanish from
 * VS Code Test Explorer. This happens because vscode-java-test detects
 * {@code @RunWith(PowerMockRunner.class)} and replaces the full static test
 * tree with partial runtime results.
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(ConfigService.class)
public class ConfigServicePowerMockTest {

    @Test
    public void testGetEnvironment() {
        PowerMockito.mockStatic(ConfigService.class);
        PowerMockito.when(ConfigService.getEnvironment()).thenReturn("production");

        assertEquals("production", ConfigService.getEnvironment());
    }

    @Test
    public void testGetMaxRetries() {
        PowerMockito.mockStatic(ConfigService.class);
        PowerMockito.when(ConfigService.getMaxRetries()).thenReturn(5);

        assertEquals(5, ConfigService.getMaxRetries());
    }

    @Test
    public void testIsDebugMode() {
        PowerMockito.mockStatic(ConfigService.class);
        PowerMockito.when(ConfigService.isDebugMode()).thenReturn(true);

        assertTrue(ConfigService.isDebugMode());
    }
}
