package com.bizrateinsights.clients;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.bizrateinsights.ExampleRequestHandler;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.List;
import java.util.stream.Collectors;

@Log4j2
public class S3OperationsClient {

    private Regions region = Regions.DEFAULT_REGION;
    private final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(region).build();
    private static final Logger LOG = LogManager.getLogger(ExampleRequestHandler.class);

    public void uploadTextFileToS3(String bucketName, String keyName, String innerText) {
        LOG.info("PUTTING FILE WITH KEY: {}", keyName);
        s3.putObject(bucketName, keyName, innerText);
    }

    public void uploadFileToS3(String bucketName, String keyName, File file) {
        s3.putObject(bucketName, keyName, file);
    }

    public List<S3ObjectSummary> getObjectSummariesInBucketWithSubkey(String bucketName, String subKey) {
        ListObjectsV2Request req = new ListObjectsV2Request().withBucketName(bucketName);
        ListObjectsV2Result result;

        do { //get all objects if more than 1000 results
            result = s3.listObjectsV2(req);
            String token = result.getNextContinuationToken();
            req.setContinuationToken(token);
        } while (result.isTruncated());

        return result.getObjectSummaries()
                .stream().filter(x -> x.getKey().contains(subKey))
                .collect(Collectors.toList());
    }
}
