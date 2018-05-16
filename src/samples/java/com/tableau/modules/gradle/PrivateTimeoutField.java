package com.tableau.modules.gradle;

import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class PrivateTimeoutField {
    @Rule
    private Timeout globalTimeout = new Timeout(5*1000L, TimeUnit.MILLISECONDS);

    @Test
    public void noopTest() {
        System.out.println("noopTest nooping right along");
    }

    @Test
    public void sleepFor10s() throws Exception {
        System.out.println("sleepFor10s test about to sleep");
        Thread.sleep(10*1000);
    }
}
