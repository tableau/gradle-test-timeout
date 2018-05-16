package com.tableau.modules.gradle;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class JunitTestWithTempFolderRule {

    @Test
    public void noopTest() {
        System.out.println("noopTest nooping right along");
    }

    @Test
    public void sleepFor10s() throws Exception {
        System.out.println("sleepFor10s test about to sleep");
        Thread.sleep(10*1000);
    }

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();
}
