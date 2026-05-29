package com.bazel.jdt;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.After;
import org.junit.Test;

public class PowerMockHelperTest {

    @After
    public void tearDown() {
        PowerMockHelper.clearCache();
    }

    @Test
    public void isPowerMockByClassName_matchesPowerMockInSimpleName() {
        assertTrue(PowerMockHelper.isPowerMockByClassName("com.example.MyPowerMockTest"));
        assertTrue(PowerMockHelper.isPowerMockByClassName("PowerMockTest"));
        assertTrue(PowerMockHelper.isPowerMockByClassName("com.foo.SomePowerMockSuite"));
    }

    @Test
    public void isPowerMockByClassName_rejectsNonPowerMockNames() {
        assertFalse(PowerMockHelper.isPowerMockByClassName("com.example.MyTest"));
        assertFalse(PowerMockHelper.isPowerMockByClassName("com.example.MockitoTest"));
        assertFalse(PowerMockHelper.isPowerMockByClassName("com.example.ServiceTest"));
        assertFalse(PowerMockHelper.isPowerMockByClassName("SimpleTest"));
    }

    @Test
    public void isPowerMockByClassName_handlesSimpleNameWithoutPackage() {
        assertTrue(PowerMockHelper.isPowerMockByClassName("PowerMockExample"));
        assertFalse(PowerMockHelper.isPowerMockByClassName("Example"));
    }

    @Test
    public void isPowerMockRunner_returnsFlaseForNull() {
        assertFalse(PowerMockHelper.isPowerMockRunner(null));
    }

    @Test
    public void isPowerMockRunner_returnsFalseForEmpty() {
        assertFalse(PowerMockHelper.isPowerMockRunner(""));
    }

    @Test
    public void isPowerMockRunner_cachesResults() {
        PowerMockHelper.isPowerMockRunner("com.example.PowerMockTest");
        assertEquals(1, PowerMockHelper.cacheSize());

        Boolean cached = PowerMockHelper.getCachedResult("com.example.PowerMockTest");
        assertNotNull(cached);
        assertTrue(cached);
    }

    @Test
    public void isPowerMockRunner_cachesNegativeResults() {
        PowerMockHelper.isPowerMockRunner("com.example.RegularTest");
        assertEquals(1, PowerMockHelper.cacheSize());

        Boolean cached = PowerMockHelper.getCachedResult("com.example.RegularTest");
        assertNotNull(cached);
        assertFalse(cached);
    }

    @Test
    public void isPowerMockRunner_cacheHitReturnsConsistentResult() {
        boolean first = PowerMockHelper.isPowerMockRunner("com.example.PowerMockTest");
        boolean second = PowerMockHelper.isPowerMockRunner("com.example.PowerMockTest");
        assertEquals(first, second);
        assertEquals(1, PowerMockHelper.cacheSize());
    }

    @Test
    public void clearCache_emptiesCache() {
        PowerMockHelper.isPowerMockRunner("com.example.PowerMockTest");
        PowerMockHelper.isPowerMockRunner("com.example.RegularTest");
        assertEquals(2, PowerMockHelper.cacheSize());

        PowerMockHelper.clearCache();
        assertEquals(0, PowerMockHelper.cacheSize());
        assertNull(PowerMockHelper.getCachedResult("com.example.PowerMockTest"));
    }

    @Test
    public void isPowerMockRunner_threadSafety() throws Exception {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return PowerMockHelper.isPowerMockRunner("com.example.PowerMockTest");
            }));
        }

        latch.countDown();

        List<Boolean> results = new ArrayList<>();
        for (Future<Boolean> f : futures) {
            results.add(f.get());
        }

        executor.shutdown();
        assertTrue("All threads should get the same result",
                results.stream().allMatch(r -> r.equals(results.get(0))));
        assertEquals(1, PowerMockHelper.cacheSize());
    }

    @Test
    public void detectPowerMockRunner_fallsBackToHeuristic() {
        assertTrue(PowerMockHelper.detectPowerMockRunner("com.example.PowerMockTest"));
        assertFalse(PowerMockHelper.detectPowerMockRunner("com.example.NormalTest"));
    }
}
