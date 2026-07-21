package com.nyberg.files.storage;

import com.nyberg.files.admin.AdminAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/storage-config")
@RequiredArgsConstructor
public class StorageProviderConfigController {

    private final StorageProviderConfigService service;
    private final AdminAccess adminAccess;

    @GetMapping
    public List<StorageProviderConfigResponse> list() {
        adminAccess.requirePlatformAdmin();
        return service.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StorageProviderConfigResponse upsert(@Valid @RequestBody StorageProviderConfigRequest req) {
        adminAccess.requirePlatformAdmin();
        return service.upsert(req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        adminAccess.requirePlatformAdmin();
        service.delete(id);
    }
}
