package com.tableau.modules.gradle;

import org.junit.Test;

public class JunitWithAnonymousInner {

    @Test
    public void usesAnonymousInner() {
        Object anon = new Object() {
            @Override
            public String toString() {
                return "usesAnonymousInner";
            }
        };
        System.out.println(anon.toString());
    }

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
