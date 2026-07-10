package com.ultracards.config;

import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/**
 * Exposes the resolved language and the full message map to every rendered
 * view, so templates can set the html lang attribute and the header fragment
 * can inject window.__I18N__ for the JS side.
 */
@ControllerAdvice
public class I18nModelAdvice {

    @ModelAttribute("i18n")
    public Map<String, String> i18n() {
        return I18nConfig.messagesFor(LocaleContextHolder.getLocale());
    }

    @ModelAttribute("lang")
    public String lang() {
        return I18nConfig.supportedLanguage(LocaleContextHolder.getLocale());
    }
}
