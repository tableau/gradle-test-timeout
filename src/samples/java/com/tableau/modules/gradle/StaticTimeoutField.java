package com.tableau.modules.gradle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.lang.Thread;
import java.lang.System;
import java.util.concurrent.TimeUnit;

public class StaticTimeoutField {
    @Rule
    public static Timeout timeout = new Timeout(5*1000L, TimeUnit.MILLISECONDS);

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
