package com.nyberg.files.admin;

import com.nyberg.files.tenant.OrganizationContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AdminAccessTest {

    private static final UUID PLATFORM = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @AfterEach
    void clear() {
        OrganizationContext.clear();
    }

    @Test
    void emptyConfigAllowsAnyCaller() {
        AdminAccess access = new AdminAccess("");
        OrganizationContext.set(OTHER);
        assertDoesNotThrow(access::requirePlatformAdmin);
    }

    @Test
    void matchingOrgAllowed() {
        AdminAccess access = new AdminAccess(PLATFORM.toString());
        OrganizationContext.set(PLATFORM);
        assertDoesNotThrow(access::requirePlatformAdmin);
    }

    @Test
    void wrongOrgForbidden() {
        AdminAccess access = new AdminAccess(PLATFORM.toString());
        OrganizationContext.set(OTHER);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, access::requirePlatformAdmin);
        assertEquals(403, ex.getStatusCode().value());
    }
}
