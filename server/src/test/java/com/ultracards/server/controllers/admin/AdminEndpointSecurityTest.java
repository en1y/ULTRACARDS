package com.ultracards.server.controllers.admin;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.UserRole;
import com.ultracards.server.service.admin.AdminAuditService;
import com.ultracards.server.service.admin.AdminGameRecordService;
import com.ultracards.server.service.games.GameAvailabilityService;
import com.ultracards.server.service.admin.AdminLobbyService;
import com.ultracards.server.service.admin.AdminNotificationService;
import com.ultracards.server.service.admin.AdminReportService;
import com.ultracards.server.service.admin.AdminStatsService;
import com.ultracards.server.service.admin.AdminSystemService;
import com.ultracards.server.service.admin.AdminUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.List;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@SpringJUnitConfig
@WebAppConfiguration
@ContextConfiguration(classes = AdminEndpointSecurityTest.TestConfiguration.class)
class AdminEndpointSecurityTest {

    private final WebApplicationContext context;
    private MockMvc mockMvc;

    AdminEndpointSecurityTest(WebApplicationContext context) {
        this.context = context;
    }

    @BeforeEach
    void setUp() {
        mockMvc = webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void anonymousRequestsAreUnauthenticatedForEveryAdminController() throws Exception {
        for (var endpoint : endpoints()) {
            mockMvc.perform(endpoint.get()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void regularUsersAreForbiddenForEveryAdminController() throws Exception {
        var authentication = authenticationForRoles(UserRole.USER);
        for (var endpoint : endpoints()) {
            mockMvc.perform(endpoint.get().with(
                            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                    .authentication(authentication)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void administratorsCanAccessEveryAdminController() throws Exception {
        var authentication = authenticationForRoles(UserRole.USER, UserRole.ADMIN);
        for (var endpoint : endpoints()) {
            mockMvc.perform(endpoint.get().with(
                            org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                    .authentication(authentication)))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    private List<Supplier<MockHttpServletRequestBuilder>> endpoints() {
        return List.of(
                () -> get("/api/admin/v1/lobbies"),
                () -> get("/api/admin/v1/users"),
                () -> get("/api/admin/v1/game-records/00000000-0000-0000-0000-000000000001"),
                () -> get("/api/admin/v1/games"),
                () -> get("/api/admin/v1/stats/users/1"),
                () -> get("/api/admin/v1/reports/overview"),
                () -> get("/api/admin/v1/audit"),
                () -> get("/api/admin/v1/system/status"),
                () -> post("/api/admin/v1/notifications/users/1")
                        .contentType("application/json")
                        .content("{\"message\":\"maintenance\",\"reason\":\"security test\"}")
        );
    }

    private Authentication authenticationForRoles(UserRole... roles) {
        var user = new UserEntity("admin@example.com", "admin");
        user.setId(1L);
        user.setRoles(new java.util.HashSet<>(List.of(roles)));
        return UsernamePasswordAuthenticationToken.authenticated(user, null, user.getAuthorities());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @EnableMethodSecurity
    static class TestConfiguration {

        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            http.csrf(csrf -> csrf.disable())
                    .anonymous(anonymous -> anonymous.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/admin/**").hasRole(UserRole.ADMIN.name())
                            .anyRequest().permitAll())
                    .exceptionHandling(errors -> errors
                            .authenticationEntryPoint((request, response, exception) -> response.setStatus(401))
                            .accessDeniedHandler((request, response, exception) -> response.setStatus(403)));
            return http.build();
        }

        @Bean AdminLobbyService adminLobbyService() { return mock(AdminLobbyService.class); }
        @Bean AdminUserService adminUserService() { return mock(AdminUserService.class); }
        @Bean AdminGameRecordService adminGameRecordService() { return mock(AdminGameRecordService.class); }
        @Bean GameAvailabilityService gameAvailabilityService() { return mock(GameAvailabilityService.class); }
        @Bean AdminStatsService adminStatsService() { return mock(AdminStatsService.class); }
        @Bean AdminReportService adminReportService() { return mock(AdminReportService.class); }
        @Bean AdminAuditService adminAuditService() { return mock(AdminAuditService.class); }
        @Bean AdminSystemService adminSystemService() { return mock(AdminSystemService.class); }
        @Bean AdminNotificationService adminNotificationService() { return mock(AdminNotificationService.class); }

        @Bean AdminLobbyController adminLobbyController(AdminLobbyService service) {
            return new AdminLobbyController(service);
        }

        @Bean AdminUserController adminUserController(AdminUserService service) {
            return new AdminUserController(service);
        }

        @Bean AdminGameRecordController adminGameRecordController(AdminGameRecordService service) {
            return new AdminGameRecordController(service);
        }

        @Bean AdminGameAvailabilityController adminGameAvailabilityController(GameAvailabilityService availability,
                                                                               AdminAuditService audit) {
            return new AdminGameAvailabilityController(availability, audit);
        }

        @Bean AdminStatsController adminStatsController(AdminStatsService service) {
            return new AdminStatsController(service);
        }

        @Bean AdminReportController adminReportController(AdminReportService service) {
            return new AdminReportController(service);
        }

        @Bean AdminAuditController adminAuditController(AdminAuditService service) {
            return new AdminAuditController(service);
        }

        @Bean AdminSystemController adminSystemController(AdminSystemService service) {
            return new AdminSystemController(service);
        }

        @Bean AdminNotificationController adminNotificationController(AdminNotificationService service) {
            return new AdminNotificationController(service);
        }
    }
}
