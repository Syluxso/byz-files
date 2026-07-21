package com.nyberg.files.file;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @GetMapping
    public List<FileResponse> list() {
        return fileService.list();
    }

    @GetMapping("/{id}")
    public FileResponse meta(@PathVariable UUID id) {
        return fileService.getMeta(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public FileResponse upload(@RequestPart("file") MultipartFile file) {
        return fileService.upload(file);
    }

    /**
     * Streams bytes through file-service (never exposes MinIO/S3 URLs).
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID id) {
        FileService.OpenFile open = fileService.openContent(id);
        StoredFile meta = open.meta();

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (meta.getContentType() != null && !meta.getContentType().isBlank()) {
            try {
                mediaType = MediaType.parseMediaType(meta.getContentType());
            } catch (Exception ignored) {}
        }

        StreamingResponseBody body = outputStream -> {
            try (open) {
                open.object().stream().transferTo(outputStream);
                outputStream.flush();
            } catch (Exception e) {
                throw new java.io.IOException("Failed streaming file content", e);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + meta.getName().replace("\"", "") + "\"")
                .contentType(mediaType)
                .contentLength(meta.getSizeBytes())
                .body(body);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        fileService.delete(id);
    }
}
