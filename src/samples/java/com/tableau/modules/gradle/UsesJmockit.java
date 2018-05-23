package com.tableau.modules.gradle;

import org.junit.Test;
import org.junit.runner.RunWith;

import mockit.Expectations;
import mockit.Injectable;
import mockit.Verifications;
import mockit.integration.junit4.JMockit;

@RunWith(JMockit.class)
public class UsesJmockit {

    @Injectable Object mockedObject;

    @Test
    public void usesBasicExpectation() throws Exception {
        new Expectations() {{
            mockedObject.toString(); result = "mocking result";
        }};
        mockedObject.toString();
        new Verifications() {{

        }};
        Thread.sleep(10*1000);
    }
}
