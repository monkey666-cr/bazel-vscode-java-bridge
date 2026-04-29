package com.example.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import org.junit.Test;

public class StringUtilsTest {

    @Test
    public void testJoinWithComma() {
        String result = StringUtils.joinWithComma(Arrays.asList("a", "b", "c"));
        assertEquals("a, b, c", result);
    }

    @Test
    public void testJoinWithCommaEmpty() {
        String result = StringUtils.joinWithComma(Arrays.asList());
        assertEquals("", result);
    }

    @Test
    public void testSplitByComma() {
        ImmutableList<String> result = StringUtils.splitByComma("hello, world, bazel");
        assertEquals(ImmutableList.of("hello", "world", "bazel"), result);
    }

    @Test
    public void testIsNullOrEmptyNull() {
        assertTrue(StringUtils.isNullOrEmpty(null));
    }

    @Test
    public void testIsNullOrEmptyEmpty() {
        assertTrue(StringUtils.isNullOrEmpty(""));
    }

    @Test
    public void testIsNullOrEmptyNotBlank() {
        assertTrue(!StringUtils.isNullOrEmpty("hello"));
    }
}
