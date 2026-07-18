package com.ultracards.server.bootstrap;

import com.ultracards.server.service.admin.AdminAuditService;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableAutoConfiguration
@EntityScan(basePackages = {"com.ultracards.server.entity", "com.ultracards.recorder"})
@EnableJpaRepositories(basePackages = "com.ultracards.server.repositories")
@Import({BootstrapAdminExecutor.class, AdminAuditService.class})
public class BootstrapAdminConfiguration {
}
