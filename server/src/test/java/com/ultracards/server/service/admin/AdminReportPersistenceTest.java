package com.ultracards.server.service.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.main.web-application-type=none",
        "app.database.startup-check.enabled=false",
        "app.mail.startup-check.enabled=false"
})
class AdminReportPersistenceTest {
    @Autowired
    private AdminReportService service;

    @Test
    void executesEveryDatabaseBackedReportQuery() {
        assertThat(service.overview()).isNotNull();
        assertThat(service.users(0, 5, null, null, null, null)).isNotNull();
        assertThat(service.games(0, 5, "BRISKULA", true, null, null)).isNotNull();
        assertThat(service.games(0, 5, "TRESETA", false, null, null)).isNotNull();
        assertThat(service.sessions(0, 5, null, true, null, null)).isNotNull();
        assertThat(service.sessions(0, 5, null, false, null, null)).isNotNull();
    }
}
