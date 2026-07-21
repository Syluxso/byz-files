package com.nyberg.files.admin;

import com.nyberg.files.file.AdminFileResponse;
import com.nyberg.files.file.AdminRenameFileRequest;
import com.nyberg.files.file.FileService;
import com.nyberg.files.file.StoredFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.UUID;

/**
 * Platform-admin file browser / CRUD for testing across all orgs.
 */
@RestController
@RequestMapping("/api/v1/admin/files")
@RequiredArgsConstructor
public class AdminFileController {

    private final FileService fileService;
    private final AdminAccess adminAccess;

    @GetMapping
    public Page<AdminFileResponse> list(
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false, defaultValue = "active") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        adminAccess.requirePlatformAdmin();
        return fileService.adminList(organizationId, status, page, size);
    }

    @GetMapping("/{id}")
    public AdminFileResponse meta(@PathVariable UUID id) {
        adminAccess.requirePlatformAdmin();
        return fileService.adminGetMeta(id);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public AdminFileResponse upload(
            @RequestParam UUID organizationId,
            @RequestPart("file") MultipartFile file) {
        adminAccess.requirePlatformAdmin();
        return fileService.adminUpload(organizationId, file);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<StreamingResponseBody> content(@PathVariable UUID id) {
        adminAccess.requirePlatformAdmin();
        FileService.OpenFile open = fileService.adminOpenContent(id);
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
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + meta.getName().replace("\"", "") + "\"")
                .contentType(mediaType)
                .contentLength(meta.getSizeBytes())
                .body(body);
    }

    @PatchMapping("/{id}")
    public AdminFileResponse rename(
            @PathVariable UUID id,
            @Valid @RequestBody AdminRenameFileRequest req) {
        adminAccess.requirePlatformAdmin();
        return fileService.adminRename(id, req.name());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        adminAccess.requirePlatformAdmin();
        fileService.adminDelete(id);
    }
}
