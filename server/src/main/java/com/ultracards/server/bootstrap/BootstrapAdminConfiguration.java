package com.ultracards.server.bootstrap;

import com.ultracards.server.service.admin.AdminAuditService;
import com.ultracards.server.service.games.UserGamesStatsService;
import com.ultracards.server.service.games.briskula.UserBriskulaStatsService;
import com.ultracards.server.service.games.treseta.UserTresetaStatsService;
import com.ultracards.server.service.users.UserService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackages = {"com.ultracards.server.entity", "com.ultracards.recorder"})
@EnableJpaRepositories(basePackages = "com.ultracards.server.repositories")
@Import({BootstrapAdminExecutor.class, UserService.class, UserGamesStatsService.class,
        UserBriskulaStatsService.class, UserTresetaStatsService.class, AdminAuditService.class})
public class BootstrapAdminConfiguration {
}
