package com.ultracards.config;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nModelAdviceTest {
    private final I18nModelAdvice advice = new I18nModelAdvice();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void marksOnlyAdministratorsForTheSharedHeader() {
        assertFalse(advice.isAdmin());

        var user = new UserEntity("admin@example.com", "admin");
        user.setRoles(new HashSet<>(List.of(UserRole.USER, UserRole.ADMIN)));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertTrue(advice.isAdmin());
        assertFalse(advice.isFakeAdmin());

        user.setFakeAdmin(true);
        assertTrue(advice.isFakeAdmin());
    }
}
