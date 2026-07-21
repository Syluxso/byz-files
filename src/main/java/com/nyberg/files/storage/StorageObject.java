package com.nyberg.files.storage;

import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.io.InputStream;

/** Open object stream; closes underlying S3 client when closed. */
public final class StorageObject implements AutoCloseable {

    private final S3Client client;
    private final InputStream stream;
    private final String contentType;
    private final Long contentLength;

    public StorageObject(S3Client client, InputStream stream, String contentType, Long contentLength) {
        this.client = client;
        this.stream = stream;
        this.contentType = contentType;
        this.contentLength = contentLength;
    }

    public InputStream stream() {
        return stream;
    }

    public String contentType() {
        return contentType;
    }

    public Long contentLength() {
        return contentLength;
    }

    @Override
    public void close() throws IOException {
        try {
            stream.close();
        } finally {
            client.close();
        }
    }
}
