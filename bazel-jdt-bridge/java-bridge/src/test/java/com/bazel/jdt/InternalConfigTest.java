package com.bazel.jdt;

import static org.junit.Assert.*;
import org.junit.Test;

public class InternalConfigTest {

    @Test
    public void testConstants() {
        assertEquals(".bazel-jdt", InternalConfig.BASE_DIR);
        assertEquals(".bazelproject", InternalConfig.CONFIG_FILE);
        assertEquals("projects", InternalConfig.PROJECTS_DIR);
        assertEquals("aspects", InternalConfig.ASPECTS_DIR);
    }

    @Test
    public void testBazelprojectRelPath() {
        assertEquals(".bazel-jdt/.bazelproject", InternalConfig.bazelprojectRelPath());
    }

    @Test
    public void testProjectsDirRelPath() {
        assertEquals(".bazel-jdt/projects", InternalConfig.projectsDirRelPath());
    }
}
