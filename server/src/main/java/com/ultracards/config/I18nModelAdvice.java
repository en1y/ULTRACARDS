package com.ultracards.config;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/**
 * Exposes shared language, canonical-site and authorization metadata to every
 * rendered view.
 */
@ControllerAdvice
public class I18nModelAdvice {
    private final I18nConfig i18nConfig;
    private final String siteUrl;

    public I18nModelAdvice(I18nConfig i18nConfig, @Value("${app.site-url}") String siteUrl) {
        this.i18nConfig = i18nConfig;
        this.siteUrl = siteUrl.replaceFirst("/+$", "");
    }

    @ModelAttribute("i18n")
    public Map<String, String> i18n() {
        return i18nConfig.messagesFor(LocaleContextHolder.getLocale());
    }

    @ModelAttribute("lang")
    public String lang() {
        return I18nConfig.supportedLanguage(LocaleContextHolder.getLocale());
    }

    @ModelAttribute("siteUrl")
    public String siteUrl() {
        return siteUrl;
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof UserEntity user && user.hasRole(UserRole.ADMIN);
    }

    @ModelAttribute("isFakeAdmin")
    public boolean isFakeAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var principal = authentication == null ? null : authentication.getPrincipal();
        return principal instanceof UserEntity user && user.isFakeAdmin();
    }
}
