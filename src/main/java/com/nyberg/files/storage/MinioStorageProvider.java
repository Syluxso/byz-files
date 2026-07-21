package com.nyberg.files.storage;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.InputStream;
import java.net.URI;
import java.util.Map;

/**
 * S3-compatible provider for MinIO (and AWS S3 with path-style disabled).
 * Credentials: endpoint, region, bucket, accessKey, secretKey, pathStyle (optional, default true).
 */
@Component
public class MinioStorageProvider implements StorageProvider {

    public static final String PROVIDER_ID = "minio";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public void ensureBucket(Map<String, String> credentials) {
        String bucket = require(credentials, "bucket");
        try (S3Client client = client(credentials)) {
            try {
                client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
            } catch (NoSuchBucketException e) {
                client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (S3Exception e) {
                if (e.statusCode() == 404) {
                    client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
                } else {
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                            "Storage bucket check failed: " + e.awsErrorDetails().errorMessage());
                }
            }
        }
    }

    @Override
    public void put(Map<String, String> credentials, String key, InputStream data, long size, String contentType) {
        String bucket = require(credentials, "bucket");
        try (S3Client client = client(credentials)) {
            PutObjectRequest.Builder req = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key);
            if (contentType != null && !contentType.isBlank()) {
                req.contentType(contentType);
            }
            client.putObject(req.build(), RequestBody.fromInputStream(data, size));
        } catch (S3Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Storage put failed: " + e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    public StorageObject get(Map<String, String> credentials, String key) {
        String bucket = require(credentials, "bucket");
        S3Client client = client(credentials);
        try {
            ResponseInputStream<GetObjectResponse> stream = client.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            GetObjectResponse meta = stream.response();
            return new StorageObject(client, stream, meta.contentType(), meta.contentLength());
        } catch (S3Exception e) {
            client.close();
            if (e.statusCode() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Object not found in storage");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Storage get failed: " + e.awsErrorDetails().errorMessage());
        }
    }

    @Override
    public void delete(Map<String, String> credentials, String key) {
        String bucket = require(credentials, "bucket");
        try (S3Client client = client(credentials)) {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Storage delete failed: " + e.awsErrorDetails().errorMessage());
        }
    }

    private static S3Client client(Map<String, String> credentials) {
        String endpoint = require(credentials, "endpoint");
        String region = credentials.getOrDefault("region", "us-east-1");
        String accessKey = require(credentials, "accessKey");
        String secretKey = require(credentials, "secretKey");
        boolean pathStyle = !"false".equalsIgnoreCase(credentials.getOrDefault("pathStyle", "true"));

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyle)
                        .build())
                .build();
    }

    private static String require(Map<String, String> credentials, String key) {
        String v = credentials.get(key);
        if (v == null || v.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing storage credential: " + key);
        }
        return v.trim();
    }
}
