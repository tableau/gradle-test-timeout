package com.tableau.modules.gradle;

import org.junit.Test;

public class BasicJunitTestWithSecondaryPrivateConstructor {

    private final boolean foo;
    private final boolean bar;

    public BasicJunitTestWithSecondaryPrivateConstructor() {
        this(true);
    }

    private BasicJunitTestWithSecondaryPrivateConstructor(boolean foo) {
        this.foo = foo;
        this.bar = !foo;
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
