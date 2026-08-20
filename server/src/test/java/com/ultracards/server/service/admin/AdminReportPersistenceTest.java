package com.ultracards.server.service.admin;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    /**
     * The advanced search binds each criterion separately. Postgres cannot infer the type
     * of a null bind inside lower(), so this runs the query with the criteria both absent
     * and supplied — the absent case is what used to fail with "lower(bytea)".
     */
    @Test
    void appliesEveryAdvancedUserCriterionTogether() {
        assertThat(service.users(0, 5, null, false, null, null, null, null, null, null, null)).isNotNull();
        assertThat(service.users(0, 5, null, false, null, null, null, null, "en", null, null)).isNotNull();
        assertThat(service.users(0, 5, null, false, null, null, null, null, null, "example.com", null)).isNotNull();
        assertThat(service.users(0, 5, null, true, "ACTIVE", "USER", "username", "asc",
                "en1y", "example.com", "1")).isNotNull();

        // Criteria are ANDed: a username that cannot also be that ID matches nothing.
        var contradiction = service.users(0, 5, null, true, null, null, null, null,
                "definitely-not-a-real-username", null, "1");
        assertThat(contradiction.items()).isEmpty();
    }

    @Test
    void rejectsANonNumericUserIdCriterion() {
        assertThatThrownBy(() -> service.users(0, 5, null, false, null, null, null, null, null, null, "abc"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
