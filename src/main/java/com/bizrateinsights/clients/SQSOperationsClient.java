package com.bizrateinsights.clients;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import lombok.extern.log4j.Log4j2;

import java.util.UUID;

@Log4j2
public class SQSOperationsClient {

    private final Regions region = Regions.DEFAULT_REGION;
    private final AmazonSQS amazonSQS = AmazonSQSClientBuilder.standard().withRegion(region).build();


    public void sendMessageToQueue(String queueName, String message) {
        String queueUrl = amazonSQS.getQueueUrl(queueName).getQueueUrl();
        SendMessageRequest sendMessageRequest = new SendMessageRequest()
                .withQueueUrl(queueUrl)
                .withMessageBody(message)
                .withMessageGroupId(String.valueOf(UUID.randomUUID()))
                .withMessageDeduplicationId(String.valueOf(UUID.randomUUID()));
        amazonSQS.sendMessage(sendMessageRequest);
    }

}
