package com.nyberg.files.storage;

import java.io.InputStream;
import java.util.Map;

public interface StorageProvider {

    String providerId();

    void ensureBucket(Map<String, String> credentials);

    void put(Map<String, String> credentials, String key, InputStream data, long size, String contentType);

    /** Caller must close the returned object (closes stream + client). */
    StorageObject get(Map<String, String> credentials, String key);

    void delete(Map<String, String> credentials, String key);
}
