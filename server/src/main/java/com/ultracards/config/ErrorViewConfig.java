package com.ultracards.config;

import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.enums.UserRole;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.ModelAndView;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class ErrorViewConfig {

    @Bean
    public ErrorViewResolver errorViewResolver() {
        return (request, status, model) -> {
            var viewModel = new LinkedHashMap<>(model);
            populateAuthModel(viewModel);

            return switch (status) {
                case NOT_FOUND -> new ModelAndView("ui/errors/404", viewModel);
                case UNAUTHORIZED -> new ModelAndView("ui/errors/401", viewModel);
                case FORBIDDEN -> new ModelAndView("ui/errors/403", viewModel);
                default -> isServerError(status) ? new ModelAndView("ui/errors/500", viewModel) : null;
            };
        };
    }

    private void populateAuthModel(Map<String, Object> model) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        var principal = authentication != null ? authentication.getPrincipal() : null;
        var user = principal instanceof UserEntity userEntity ? userEntity : null;
        var isAuthenticated = user != null;

        model.put("isAuthenticated", isAuthenticated);
        model.put("isAdmin", isAuthenticated && user.hasRole(UserRole.ADMIN));
        if (isAuthenticated) {
            model.put("username", user.getUsername());
        }

        // Error views bypass @ControllerAdvice model attributes, so the i18n
        // attributes from I18nModelAdvice have to be re-added by hand here.
        var locale = LocaleContextHolder.getLocale();
        model.put("i18n", I18nConfig.messagesFor(locale));
        model.put("lang", I18nConfig.supportedLanguage(locale));
    }

    private boolean isServerError(HttpStatus status) {
        return status.is5xxServerError() || status == HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
