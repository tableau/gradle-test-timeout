package com.tableau.modules.gradle;

import java.util.concurrent.TimeUnit;

import org.junit.Rule;
import org.junit.rules.Timeout;

public class ClassWithTimeoutButNoTests {
    @Rule
    public Timeout timeout = new Timeout(5*1000L, TimeUnit.MILLISECONDS);
}
