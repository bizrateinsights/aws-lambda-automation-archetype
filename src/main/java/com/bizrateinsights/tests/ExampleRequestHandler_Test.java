package com.bizrateinsights.tests;

import com.bizrateinsights.ExampleRequestHandler;
import com.bizrateinsights.selenium.Browsers;
import com.bizrateinsights.selenium.Devices;
import com.bizrateinsights.JunitUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

@Log4j2
public class ExampleRequestHandler_Test extends DataConfig {

    private static final Logger LOG = LogManager.getLogger(ExampleRequestHandler.class);

    @Test
    public void test() {
        webDriver = lambdaWebdriverFactory.getWebdriver(Browsers.CHROMIUM, Devices.DESKTOP);

        webDriver.get("https://www.google.com");
        LOG.info("Current URL: {}", webDriver.getCurrentUrl());
        Assert.assertTrue(webDriver.getCurrentUrl().contains("google"));
        webDriver.quit();
    }

    @Test
    public void test2() {
        List<String> allClasses = com.bizrateinsights.JunitUtils.getJunitTestClasses("com.automationlambda");
        LOG.info(allClasses);
        for (String clazz : allClasses) {
            Assert.assertNotNull(JunitUtils.getTestsInJunitClass(clazz));
        }
    }

    @Test
    public void failureTest() {
        webDriver = lambdaWebdriverFactory.getWebdriver(Browsers.CHROMIUM, Devices.DESKTOP);
        webDriver.get("https://www.google.com");
        LOG.info("INTENTIONALLY FAILING TEST");
        Assert.fail("Test failed!");
    }

}
