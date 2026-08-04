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
        assertThat(service.overview().completedGames()).containsKeys("BRISKULA", "DURAK", "TRESETA");
        assertThat(service.overview().incompleteGames()).containsKeys("BRISKULA", "DURAK", "TRESETA");
        assertThat(service.users(0, 5, null, null, null, null)).isNotNull();
        assertThat(service.users(0, 5, "en", null, null, "username", "asc")).isNotNull();
        assertThat(service.users(0, 5, "en1y", true, null, null, "username", "asc")).isNotNull();
        assertThat(service.games(0, 5, "BRISKULA", true, null, null)).isNotNull();
        assertThat(service.games(0, 5, "DURAK", true,
                "P2_D24_NO_JOKERS_NEIGHBORS_NO_PASS", null, null)).isNotNull();
        assertThat(service.games(0, 5, "TRESETA", false, null, null)).isNotNull();
        assertThat(service.sessions(0, 5, null, true, null, null)).isNotNull();
        assertThat(service.sessions(0, 5, null, false, null, null)).isNotNull();
        assertThat(service.database().recordsByArea()).containsKey("Durak stat rows");
    }
}
