package com.bizrateinsights.model;

import org.aeonbits.owner.Config;

@Config.Sources("classpath:meta.properties")
public interface MetaConfig extends Config{

    @Key("webdriver.local")
    Boolean getWebdriverIsLocal();

    @Key("remote.artifacts.bucket")
    String getRemoteArtifactsBucket();

    @Key("remote.results.bucket")
    String getRemoteResultsBucket();

    @Key("chromedriver.local.path")
    String getChromedriverLocalPath();

    @Key("chromedriver.remote.path")
    String getChromedriverRemotePath();

    @Key("scaled.testing.artifacts.bucket")
    String getScaledTestingArtifactsBucket();

    @Key("sqs.name")
    String getSQSQueue();

    @Key("max.retry.count")
    Integer getMaxTestRetryCount();

    @Key("slack.hook")
    String getSlackHook();

    @Key("implicit.wait.seconds")
    Integer defaultImplicitWaitSeconds();

    @Key("expected.condition.timeout.seconds")
    Integer defaultExpectedConditionTimeoutSeconds();

    @Key("new.tab.timeout.seconds")
    Integer defaultNewTabTimeoutSeconds();

}

