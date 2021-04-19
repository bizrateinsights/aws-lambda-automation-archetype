package com.bizrateinsights;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.bizrateinsights.clients.S3OperationsClient;
import com.bizrateinsights.clients.SQSOperationsClient;
import com.bizrateinsights.model.MetaConfig;
import com.google.gson.Gson;
import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.runner.Result;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.MediaType;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;


/**
 * This is the Lambda request handler, which provides an entry point for AWS Lambda to invoke the Lambda function code.
 * <p>
 * General flow of request handling goes like this:
 * <p>
 * "{run: all}" json -> [Lambda] -> [SQS message for each test] -> [Lambda Invocation for Each Test]
 */
public class ExampleRequestHandler implements RequestHandler<Map<String, Object>, Boolean> {

    private static final Logger LOG = LogManager.getLogger(ExampleRequestHandler.class);
    private static final MetaConfig CONFIG = ConfigFactory.create(MetaConfig.class);
    public static final int MAX_RETRY_COUNT = CONFIG.getMaxTestRetryCount();

    /**
     * Get test count without filter
     * @param pkg = base package
     * @return test case count
     */
    private Integer getTestCount(String pkg) {
        AtomicReference<Integer> count = new AtomicReference<>(0);
        List<String> allClasses = JunitUtils.getJunitTestClasses(pkg);
        allClasses.forEach(x -> count.updateAndGet(v -> v + JunitUtils.getTestsInJunitClass(x).size()));
        return count.get();
    }

    /**
     * Get test count with a filter on method names
     * @param pkg = base package
     * @param filter = filter on testnames
     * @return test case count
     */
    private Integer getTestCount(String pkg, String filter) {
        AtomicReference<Integer> count = new AtomicReference<>(0);
        List<String> allClasses = JunitUtils.getJunitTestClasses(pkg);
        allClasses.forEach(x -> count.updateAndGet(v -> v + JunitUtils.getTestsInJunitClass(x, filter).size()));
        return count.get();
    }

    private boolean keyIsFailure(S3ObjectSummary objectSummary){
        return objectSummary.getKey().contains("~false"); //key contains junit success value of "false"
    }

    private boolean keyContainsRetry(S3ObjectSummary objectSummary){
        return !objectSummary.getKey().contains("RetryCount:0"); //key does not have retry count of 0
    }

    private String getFailureReporting(List<S3ObjectSummary> objectSummaries) {
        String failureReports = "";
        for (S3ObjectSummary objectSummary : objectSummaries) {
            if (keyIsFailure(objectSummary) || keyContainsRetry(objectSummary))
                failureReports += objectSummary.getKey() + "\n";
        }
        if (failureReports.isEmpty()){
            return "No failures to report!";
        }
        return failureReports;
    }

    /**
     * send a slack message with a simple report showing the test failures and retries. While the default reporting uses
     * slack, it is recommended you update the reporting to integrate with your current workflow.
     *
     * @param message = the message that is sent to slack
     */
    private void sendSlackMessage(String message) {
        Client client = ClientBuilder.newClient();
        Invocation.Builder invocationBuilder = client.target(CONFIG.getSlackHook())
                .request(MediaType.APPLICATION_JSON_TYPE);

        //update this to fit the needs of your project - you may have to set up your own slackbot on your workspace
        invocationBuilder.post(Entity.json("{\"channel\": \"#your-slack-channel-here\", \"username\": \"Default\", \"text\": \"" + message + "\", \"icon_emoji\": \":sunglasses:\"}"));
    }

    private void insertTestTrggerIntoSQS(String clazz, String test, String runId, String testId, Integer testCount, Integer retryCount) {
        SQSOperationsClient sqsOperationsClient = new SQSOperationsClient();
        String json = String.format(
                "{\"class\": \"%s\", " +
                        "\"method\": \"%s\", " +
                        "\"runId\": \"%s\", " +
                        "\"testId\": \"%s\", " +
                        "\"testCount\": \"%s\"," +
                        "\"retryCount\": \"%s\"}",
                clazz, test, runId, testId, testCount, retryCount);

        LOG.info("INVOKING SUITE WITH {}", json);
        sqsOperationsClient.sendMessageToQueue(CONFIG.getSQSQueue(), json);
    }

    //{ "run": "all" }
    private Boolean handleEntry(Map<String, Object> event) {
        String testSuiteRunId = String.valueOf(UUID.randomUUID());
        Integer testTotalCount = getTestCount("com.automationlambda");
        List<String> allClasses = JunitUtils.getJunitTestClasses("com.automationlambda");

        sendSlackMessage("Starting Example Archetype Lambda! \n" +
                "Testcount - " + testTotalCount + "\n" +
                "SuiteRunId - " + testSuiteRunId);

        for (String clazz : allClasses) {
            for (String test : JunitUtils.getTestsInJunitClass(clazz)) {
                String testIndividualId = String.valueOf(UUID.randomUUID());
                insertTestTrggerIntoSQS(clazz, test, testSuiteRunId, testIndividualId, testTotalCount, 0);
            }
        }

        return true;
    }

    //{ "run" : "method", "class" : "className", "method" : "methodName" }
    private Boolean handleMethodEntry(Map<String, Object> event){

        String testClass = (String)event.get("class");
        String testMethod = (String)event.get("method");
        String testSuiteRunId = String.valueOf(UUID.randomUUID());
        String testIndividualId = String.valueOf(UUID.randomUUID());
        Integer testTotalCount = 1;
        sendSlackMessage("Starting Example Archetype Lambda (Singular Test)! \n" +
                "Test Method - " + testMethod + "\n" +
                "Test Class - " + testClass);
        insertTestTrggerIntoSQS(testClass, testMethod, testSuiteRunId, testIndividualId, testTotalCount, 0);

        return true;
    }

    //{ "run" : "filter", "nameContains" : "string" }
    private Boolean handleFilterEntry(Map<String, Object> event) {
        String testSuiteRunId = String.valueOf(UUID.randomUUID());
        String filter = (String)event.get("nameContains");
        Integer testTotalCount = getTestCount("com.automationlambda", filter);
        List<String> allClasses = JunitUtils.getJunitTestClasses("com.automationlambda");

        for (String clazz : allClasses) {
            for (String test : JunitUtils.getTestsInJunitClass(clazz, filter)) {
                String testIndividualId = String.valueOf(UUID.randomUUID());
                insertTestTrggerIntoSQS(clazz, test, testSuiteRunId, testIndividualId, testTotalCount, 0);
            }
        }

        sendSlackMessage("Starting Example Archetype Lambda (Filtered Tests)! \n" +
                "Filter - " + filter + "\n" +
                "Testcount - " + testTotalCount + "\n" +
                "SuiteRunId - " + testSuiteRunId);

        return true;
    }

    //sqs event
    private Boolean handleTestRun(Map<String, Object> event) {
        LOG.info("INGESTING FROM SQS: {}", event.toString());

        Gson gson = new Gson();
        S3OperationsClient s3OperationsClient = new S3OperationsClient();

        List<Map<String, Object>> record = (List<Map<String, Object>>) event.get("Records");
        String bodyString = (String) record.get(0).get("body");
        Map<String, String> bodyMap = gson.fromJson(bodyString, Map.class);

        String testClass = bodyMap.get("class");
        String testMethod = bodyMap.get("method");
        String testSuiteRunId = bodyMap.get("runId");
        String testIndividualId = bodyMap.get("testId");
        String testTotalCount = bodyMap.get("testCount");
        String retryCount = bodyMap.get("retryCount");

        LOG.info("Starting Test: SuiteID:{} - TestID:{} - Class:{} - Method:{}", testSuiteRunId, testIndividualId, testClass, testMethod);
        Result result = JunitUtils.runJunitTest(testClass, testMethod);
        result.getFailures().forEach(x->LOG.info(x.getTrace())); //log errors in cloudwatch

        String keyName = testSuiteRunId + "/" + testIndividualId + "~" + testMethod + "~RetryCount:" + retryCount + "~" + result.wasSuccessful();

        if (!result.wasSuccessful()) {

            try {
                s3OperationsClient.uploadFileToS3(CONFIG.getRemoteArtifactsBucket(), keyName + ".png", new File("/tmp/screenshot.png"));
            } catch (Exception e) {
                LOG.info("No screenshot recorded.");
            }

            if (Integer.parseInt(retryCount) < MAX_RETRY_COUNT) {
                String retryTestId = String.valueOf(UUID.randomUUID());
                insertTestTrggerIntoSQS(testClass, testMethod, testSuiteRunId, retryTestId, Integer.parseInt(testTotalCount), Integer.parseInt(retryCount) + 1);
                return false; //test failed, but don't execute teardown before retrying
            }

        }

        s3OperationsClient.uploadTextFileToS3(CONFIG.getRemoteResultsBucket(), keyName, result.toString());

        //suite ending trigger - all test results uploaded to s3
        List<S3ObjectSummary> objectSummaries = s3OperationsClient.getObjectSummariesInBucketWithSubkey(CONFIG.getRemoteResultsBucket(), testSuiteRunId);
        if (objectSummaries.size() == Integer.parseInt(testTotalCount)) {
            long testRetryCount = objectSummaries.stream().filter(x -> !x.getKey().contains("RetryCount:0")).count();
            long failureCount = objectSummaries.stream().filter(x -> x.getKey().contains("~false")).count();
            String failureReporting = getFailureReporting(objectSummaries);
            sendSlackMessage(testSuiteRunId + " - Example Archetype Lambda has finished running all tests! \n" +
                    "Tests Run: " + testTotalCount + ", Tests Failed: " + failureCount + ", Tests Retried: " + testRetryCount + "\n" +
                    "[Suite Run ID]/[Test Run Id]~[Method]~[Retries]~[Was Successful]: \n" + failureReporting);
        }
        FileUtils.deleteQuietly(new File("/tmp/")); //force tmp directory clear
        return result.wasSuccessful();
    }

    @Override
    public Boolean handleRequest(Map<String, Object> event, Context context) {
        if (event.containsKey("run") && event.get("run").equals("all")) { //run all tests
            return handleEntry(event);
        } else if (event.containsKey("run") && event.get("run").equals("method")) { //run single test
            return handleMethodEntry(event);
        }else if (event.containsKey("run") && event.get("run").equals("filter")){ //run tests with method filter
            return handleFilterEntry(event);
        } else if (event.containsKey("Records")) { //sqs event
            return handleTestRun(event);
        } else {
            throw new IllegalArgumentException("Lambda attempted to be invoked with non-supported JSON");
        }
    }

    /**
     * A list of environment variables which are configured in AWS.
     */
    enum EnvironmentVariable {
        ENVIRONMENT
    }

    /**
     * Returns the value of the specified environment variable.
     *
     * @param var an environment variable
     * @return the value of the variable {@code var}
     */
    static String env(EnvironmentVariable var) {
        return System.getenv(var.name());
    }

}
