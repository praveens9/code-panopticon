package com.repo.analyzer;

import org.junit.jupiter.api.Test;

public class DirtyTest {

    @Test
    public void testWithSleep() throws InterruptedException {
        // Intentional "Hardcoded Wait" to trigger detector
        Thread.sleep(5000);

        // Intentional "Assertion Roulette" (pseudo-code patterns)
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something");
        verify("something"); // > 20
    }

    // Simulate generic verify method matches detection pattern
    private void verify(String s) {
    }
}
