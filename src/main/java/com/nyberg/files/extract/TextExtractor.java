package com.nyberg.files.extract;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Best-effort body text extraction for search indexing (PDF, Office, plain text, …).
 */
@Slf4j
@Component
public class TextExtractor {

    private final int maxChars;

    public TextExtractor(@Value("${byz.files.extract.max-chars:500000}") int maxChars) {
        this.maxChars = Math.max(10_000, maxChars);
    }

    public String extract(byte[] bytes, String contentType, String filename) {
        if (nilOrEmpty(bytes)) {
            return "";
        }
        // Fast path for plain text / markdown / csv.
        if (isPlainText(contentType, filename)) {
            String s = new String(bytes, StandardCharsets.UTF_8);
            return truncate(s);
        }
        try {
            // BodyContentHandler(-1) = unlimited; we truncate ourselves.
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            if (contentType != null && !contentType.isBlank()) {
                metadata.set(Metadata.CONTENT_TYPE, contentType);
            }
            if (filename != null && !filename.isBlank()) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);
            }
            AutoDetectParser parser = new AutoDetectParser();
            try (InputStream in = new ByteArrayInputStream(bytes)) {
                parser.parse(in, handler, metadata, new ParseContext());
            }
            return truncate(handler.toString());
        } catch (IOException | SAXException | TikaException | RuntimeException e) {
            log.warn("text extract failed name={} type={}: {}", filename, contentType, e.toString());
            return "";
        }
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        String cleaned = s.replace('\u0000', ' ').trim();
        if (cleaned.length() <= maxChars) {
            return cleaned;
        }
        return cleaned.substring(0, maxChars);
    }

    private static boolean nilOrEmpty(byte[] bytes) {
        return bytes == null || bytes.length == 0;
    }

    private static boolean isPlainText(String contentType, String filename) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        if (ct.startsWith("text/") || ct.contains("json") || ct.contains("xml")) {
            return true;
        }
        String name = filename == null ? "" : filename.toLowerCase();
        return name.endsWith(".txt") || name.endsWith(".md") || name.endsWith(".csv")
                || name.endsWith(".json") || name.endsWith(".log");
    }
}
