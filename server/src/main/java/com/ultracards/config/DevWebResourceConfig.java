package com.ultracards.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;

import java.nio.file.Files;
import java.nio.file.Path;

@Configuration
@ConditionalOnProperty(name = "app.debug", havingValue = "true")
public class DevWebResourceConfig implements WebMvcConfigurer {

    private static final String[] TEMPLATE_SOURCE_CANDIDATES = {
            "src/main/resources/templates",
            "server/src/main/resources/templates"
    };

    private static final String[] STATIC_SOURCE_CANDIDATES = {
            "src/main/resources/static",
            "server/src/main/resources/static"
    };

    @Bean
    public SpringResourceTemplateResolver fileSystemTemplateResolver(ApplicationContext applicationContext) {
        var resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(applicationContext);
        resolver.setPrefix(resolveResourceLocation(TEMPLATE_SOURCE_CANDIDATES));
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        resolver.setCacheable(false);
        resolver.setCheckExistence(true);
        resolver.setOrder(0);
        return resolver;
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        var staticLocation = resolveExistingResourceLocation(STATIC_SOURCE_CANDIDATES);
        if (staticLocation == null) {
            return;
        }

        registry.addResourceHandler("/**")
                .addResourceLocations(staticLocation, "classpath:/static/")
                .setCacheControl(CacheControl.noStore().mustRevalidate());
    }

    private String resolveResourceLocation(String[] candidates) {
        var existingLocation = resolveExistingResourceLocation(candidates);
        return existingLocation != null ? existingLocation : "classpath:/templates/";
    }

    private String resolveExistingResourceLocation(String[] candidates) {
        for (String candidate : candidates) {
            var absolutePath = Path.of(candidate).toAbsolutePath().normalize();
            if (Files.isDirectory(absolutePath)) {
                return absolutePath.toUri().toString() + "/";
            }
        }
        return null;
    }
}
