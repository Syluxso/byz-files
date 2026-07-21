package com.nyberg.files.admin;

import com.nyberg.files.tenant.OrganizationContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Gates {@code /api/v1/admin/**} to the platform (byz-admin) organization when
 * {@code byz.files.admin-organization-id} is set. Empty = any authenticated JWT
 * (local/dev). Prod should set the byz-admin org UUID.
 */
@Component
public class AdminAccess {

    private final UUID adminOrganizationId;

    public AdminAccess(
            @Value("${byz.files.admin-organization-id:}") String adminOrganizationId) {
        this.adminOrganizationId = parseOptionalUuid(adminOrganizationId);
    }

    public void requirePlatformAdmin() {
        if (adminOrganizationId == null) {
            return;
        }
        UUID callerOrg = OrganizationContext.get();
        if (callerOrg == null || !adminOrganizationId.equals(callerOrg)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the platform admin organization can manage this resource");
        }
    }

    private static UUID parseOptionalUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "byz.files.admin-organization-id must be a UUID, got: " + raw);
        }
    }
}
