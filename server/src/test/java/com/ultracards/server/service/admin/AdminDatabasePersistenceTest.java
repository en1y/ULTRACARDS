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
class AdminDatabasePersistenceTest {
    @Autowired
    private AdminDatabaseService service;

    @Test
    void listsNotificationRowsAgainstPostgres() {
        assertThat(service.notifications(0, 20)).isNotNull();
    }
}
