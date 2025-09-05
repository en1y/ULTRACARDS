package com.ultracards.webui.config;

import com.ultracards.webui.controllers.HeartbeatController;
import com.ultracards.webui.service.GameNavigationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration class.
 * Configures additional web application settings beyond Spring Boot's auto-configuration.
 * 
 * Note: Most of the configuration is now handled automatically by Spring Boot.
 * This class only contains customizations beyond the default configuration.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {


    private final GameNavigationInterceptor navigationInterceptor;

    public WebMvcConfig(GameNavigationInterceptor navigationInterceptor) {
        this.navigationInterceptor = navigationInterceptor;
    }

    /**
     * Configures the resource handlers for static resources.
     * This is only needed if you want to customize the default resource locations.
     * Spring Boot automatically serves static content from /static, /public, /resources, and /META-INF/resources
     * 
     * @param registry The resource handler registry
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // These configurations are actually redundant with Spring Boot defaults
        // but are kept for clarity and in case custom paths are needed in the future
        registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(navigationInterceptor)
                .addPathPatterns("/**");
    }
}