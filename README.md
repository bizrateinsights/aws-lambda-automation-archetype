# Automation-Lambda-Framework

The purpose of this project is to create an automation framework that can run a very large number of tests in a very
short amount of time. This framework allows you to reduce the time required to run very large test suites from a 
huge number of hours to minutes, as all tests are run in parallel.

This makes suite maintenance easier, and allows for identifying test flakiness and race conditions in a more timely manner.

# Basic Overview:
![automation-diagram](https://user-images.githubusercontent.com/12022018/115311128-f74edc80-a123-11eb-940b-dd71e12a2f6d.png)

- Runner Json - The entry point of the lambda 

- Framework (Setup Mode) - The lambda looks for all classes with with names ending in "_Test", and inserts the testrunner json into the specified SQS queue.

- Test Runner Json - Inserted into an SQS queue during setup mode. Is used to re-invoke itself so it can run each specified test inserted into the SQS queue.

- Framework (Running Single Test) - The framework spins up one lambda per test, and allows all tests to be run in parallel. After test completion, it stores the results and artifacts into the specified S3 buckets.

# Setup Instructions:

1) Fork this code into a new project, set it up with your preferred method for uploading code to AWS, and create a new lambda. 
Rename and point the framework's handle requests method at it.

2) Set up any needed aliases on the lambda, so you can properly deploy your code.

3) Set up the lambda to use a reasonable amount of resources. Most existing lambda automation projects use the 1GB memory 
version of AWS Lambda, since that is what seems to run the version of chrome in the S3 bucket in a stable way.

4) Rename any classes / methods as needed for your project

5) Set up three new s3 buckets for the automation results, screenshots, and a bucket holding a chromedriver instance and a 
custom installation of chrome. A modified version of chrome that can run on AWS lambda instances can be found online, but be sure to make sure your version 
of chromedriver matches that version of chrome. Ensure their names are set properly in the `meta.properties` file of your project. 

6) It is recommended you set a rule for auto deletion after a period of time on the automation results and screenshot buckets.

7) Set up an SQS FIFO queue. Hook it up to the lambda input. For the lambda to work properly, it must
processes 1 at a time. Ensure this is the case.

8) Set up all other values in the `meta.properties` file. Ensure they are correct.

9) Change the slack url in the request handler if needed.

10) Rename any methods/classes as needed.

11) Test run your framework - there should be two test successes and one test failure. Continue with local setup if you
wish to develop on your native machine!

# Local Setup:

1) Download a chrome webdriver binary at https://chromedriver.chromium.org/. 
Ensure that the version you download matches the version of your chrome browser.

2) Add the webdriver binary to resources/webdriver/chromedriver, or whatever filepath
is designated in meta.properties. Its untracked from git by default, so don't worry about committing the large file.

3) You can now run things locally through your IDE by setting the meta.properties
local value to true. Ensure it is set to false before you run it remotely again.

Note: If you run your tests in intellij, you can run tests the same as any other
junit class - right click on the test or the class, and click run.

Common errors when running tests:

```
Webdriver permissions on mac - you might have to go to where your webdriver binary
is and set up permissions in terminal for it to run on mac properly

Webdriver version not supported by browser version - if the version of webdriver
you are running is not supported by the browser version you have installed on your
machine, you might have to redownload a new webdriver binary and replace the old one.
```

# Starting the suite remotely:

It is recommended you hook the framework lambda to a service such as API gateway so that you can trigger it automatically
with your CI pipelines. However, if you want to run it on AWS, you can simply send a test event to the lambda.

By default, the lambda archetype supports three modes of execution

Run singular test json:
```
{
  "run": "method",
  "class": "com.bizrateinsights.tests.ExampleRequestHandler_Test",
  "method": "test2",
}
```
Run all tests json:
```
{
  "run": "all",
}
```
Run with method filter json:
```
{
"run": "filter",
"nameContains": "3"
}
```
# Slack Reporting

By default, it should give a starting signal to webdev-general. If you want to change this,
you can set up a slack bot and update the slack hook in the `meta.properties` file.

After every test execution, the suite checks to see if the number of tests in the designated
test run matches the number of artifacts in the s3 test results bucket. If so, it
sends a report of any failures and retries to the same slack channel.

# In Depth Details:

### Runner Json
As of now, the main mode of running the lambda framework is by invoking the following json:

{"run": "all"}

This can be extended into the future for framework specific options, such as custom timeouts, custom scoping, etc.

### Framework - SetupMode
In this mode, it uses java reflection to search for all java classes marked with "_Test" and inserts Test Runner Json into a specified SQS queue. The json contains the following fields:

```
class - The class that the test method to be run is located in

method - The method that is to be run by a single lambda invocation

runId - a UUID to separate test suite runs from eachother. Generated at the beginning of the suite.

testId - a UUID to ensure that each test has a unique identity

testCount - the total number of tests in the suite. This is so that when results are generated, a lambda invocation can identify when the test suite has completed running.

```

### Framework Specific SQS queue
A FIFO SQS queue must be hooked up to the input of the automation lambda for it to function properly. The reason why it is a fifo queue is so that delivery to the lambda is ensured EXACTLY once. There does not need to be an output SQS queue.

When the test runner json is inserted into the queue by the automation lambda, the same Lambda is designed to pick up those same SQS messages concurrently in the designated batch amounts. This ensures that all tests are run concurrently, and suite times are able to approach theoretically optimal runtimes.

### Framework (Running Single Test)
This is where the SQS message is processed, and a custom Junit runner runs a singular test. As needed, it downloads the webdriver binaries to the lambda tmp directory from S3. For the time being, webdriver setup is is simplified by just putting what you need in a designated s3 bucket, and letting the framework handle the rest.

All tests run concurrently. On average, suite runtimes should take about 3-5 minutes to finish assuming you follow best practices when creating tests. Upon finishing a test, it returns true if the Junit runner returns a success, or false if the Junit runner returned a failure. This can be used to query the cloudwatch logs generated from the invocations if you want to search for and investigate failures.

### Test Run Storage - S3
Whenever a test finishes, the results are stored in a suite specific s3 bucket with the following Key:

```
[Suite Run UUID]/[Test Run UUID]~[Junit Method]~[Retry Count]~[Success value (true/false)]
```

And whenever there is a failure, it sends a message to the designated slack channel. In addition, whenever the lambda inserts a test result, it counts the number of results with a specific Suite Run UUID. If the number of tests in the bucket equals the number of tests the json declares the suite is supposed to run, it declares the suite to be finished running.

If a Webdriver instance was declared by a test, Screenshots for failures are stored in the s3 bucket designated by the suite.

NOTE: When using this framework, be sure to set the TTL on the buckets to expire after a period of time. The default that we have been using is 2 weeks, but feel free to change this as needed.

# Current Limitations:

As of right now, the suite only runs using chromedriver for frontend tests, and cannot run tests longer than 10 minutes 
due to the limitations of AWS lambda. If you require more specialized use cases for your test automation, it is recommended
you use another framework.
