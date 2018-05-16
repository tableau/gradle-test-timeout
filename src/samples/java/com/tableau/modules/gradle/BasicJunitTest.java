package com.tableau.modules.gradle;

import org.junit.Test;
import java.lang.Thread;
import java.lang.System;

public class BasicJunitTest {

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
