package com.bizrateinsights.tests;

import com.bizrateinsights.ExampleRequestHandler;
import com.bizrateinsights.selenium.LambdaWebdriverFactory;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.Augmenter;

import java.io.File;
import java.io.IOException;

@Log4j2
public class DataConfig {

    WebDriver webDriver;
    LambdaWebdriverFactory lambdaWebdriverFactory;
    private static final Logger LOG = LogManager.getLogger(ExampleRequestHandler.class);

    private void addDeleteFileShutdownHook(File file) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> FileUtils.deleteQuietly(file)));
    }

    private File takeScreenshot(WebDriver webDriver) {
        WebDriver augmentedDriver = new Augmenter().augment(webDriver);
        return ((TakesScreenshot) augmentedDriver).getScreenshotAs(OutputType.FILE);
    }

    private void saveScreenshot(WebDriver webDriver, String destinationPath) {
        try {
            File source = takeScreenshot(webDriver);
            FileUtils.copyFile(source, new File(destinationPath));
            addDeleteFileShutdownHook(source); //delete from default path
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Before
    public void setup() {
        lambdaWebdriverFactory = new LambdaWebdriverFactory();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> webDriver.quit()));
    }


    @After
    public void teardown() {
            try{
                //screenshot handled after junit results. Otherwise stored to temp directory and deleted after lambda execution.
                saveScreenshot(webDriver, "/tmp/screenshot.png");
            } catch (Exception e) {
                //eat exception. Webdriver not guaranteed to be declared in all tests.
            }
    }
}
