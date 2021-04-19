package com.bizrateinsights.selenium;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.bizrateinsights.model.MetaConfig;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.aeonbits.owner.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

@Log4j2
@AllArgsConstructor
public class LambdaWebdriverFactory {

    public static final int DEFAULT_IDLE_TIMEOUT = 120;
    private static final MetaConfig CONFIG = ConfigFactory.create(MetaConfig.class);
    private static final Logger LOG = LogManager.getLogger(LambdaWebdriverFactory.class);

    /**
     * Download a file if it exists, and do nothing if already present.
     * This is because AWS lambda has a tendency to share the temp directory between invocations.
     */
    private void downloadNonExistingFile(S3Object s3Object, String filePath) throws IOException {
        if (!Paths.get("/tmp/" + filePath).toFile().exists()) {
            LOG.info("DOWNLOADING - {}", filePath);
            InputStream in = s3Object.getObjectContent();
            Files.copy(in, Paths.get("/tmp/" + filePath));
            File file = new File("/tmp/" + s3Object.getKey());
            file.setExecutable(true, false);
        }
    }

    private void setChromedriverPath() {
        if(!CONFIG.getWebdriverIsLocal()) {
            System.setProperty("webdriver.chrome.driver", CONFIG.getChromedriverRemotePath());
        } else {
            System.setProperty("webdriver.chrome.driver", CONFIG.getChromedriverLocalPath());
        }

    }

    private void setupBinaries() {
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.DEFAULT_REGION).build();
        final String bucketName = CONFIG.getScaledTestingArtifactsBucket();

        try {
            downloadNonExistingFile(s3.getObject(new GetObjectRequest(bucketName, "chromedriver")), "chromedriver");
            downloadNonExistingFile(s3.getObject(new GetObjectRequest(bucketName, "headless-chromium")), "headless-chromium");
        } catch (Exception e) {
            LOG.info(e);
            throw new RuntimeException(e);
        }
    }

    private WebDriver getChromeBrowser(Devices device) {
        ChromeOptions chromeOptions = new ChromeOptions();

        if(!CONFIG.getWebdriverIsLocal()){
            chromeOptions.setBinary("/tmp/headless-chromium");
            chromeOptions.addArguments("--headless");
            chromeOptions.addArguments("--disable-dev-shm-usage");
            chromeOptions.addArguments("--no-sandbox");
            chromeOptions.addArguments("--disable-gpu");
            chromeOptions.addArguments("--user-data-dir=/tmp/user-data");
            chromeOptions.addArguments("--hide-scrollbars");
            chromeOptions.addArguments("--enable-logging");
            chromeOptions.addArguments("--log-level=0");
            chromeOptions.addArguments("--v=99");
            chromeOptions.addArguments("--single-process");
            chromeOptions.addArguments("--data-path=/tmp/data-path");
            chromeOptions.addArguments("--ignore-certificate-errors");
            chromeOptions.addArguments("--homedir=/tmp");
            chromeOptions.addArguments("--disk-cache-dir=/tmp/cache-dir");
            chromeOptions.addArguments("--user-agent=Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/61.0.3163.100 Safari/537.36");
        }

        switch (device) {
            case DESKTOP:
                chromeOptions.addArguments("--window-size=5000,2000");
                break;
            case MOBILE:
                chromeOptions.addArguments("--window-size=400,800");
                break;
            default:
                throw new RuntimeException("Device not Supported!");

        }

        return new ChromeDriver(chromeOptions);
    }

    /**
     * Set up custom webdriver options for browsers
     *
     * @param browser = type of browser (Chrome or Firefox)
     * @return custom setup webdriver
     */
    private WebDriver getCustomWebdriver(Browsers browser, Devices device) throws Exception {
        switch (browser) {
            case CHROMIUM:
                return getChromeBrowser(device);
            default:
                throw new Exception("Browser Not Supported!");
        }
    }

    @SneakyThrows
    public WebDriver getWebdriver(Browsers browser, Devices device) {
        if(!CONFIG.getWebdriverIsLocal()) {
            setupBinaries();
        }
        setChromedriverPath();

        final WebDriver webDriver = getCustomWebdriver(browser, device);
        //set default timeout for finding elements on a page
        webDriver.manage().timeouts().implicitlyWait(DEFAULT_IDLE_TIMEOUT, TimeUnit.SECONDS);

        return webDriver;
    }

    public WebDriver getDefaultWebdriver() {
        return getWebdriver(Browsers.CHROMIUM, Devices.MOBILE);
    }


}
