package com.ultracards.ui.webui.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<?> handleHttpClientError(
            HttpClientErrorException ex,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var uri = request.getRequestURI();

        // In case of trying to change your email but a user with such an email already exists
        if (uri.startsWith("/api/auth/email/send") && ex.getStatusCode().equals(HttpStatus.CONFLICT)) {
            // Try to forward the upstream JSON body (if any) so the UI can display all messages
            try {
                var raw = ex.getResponseBodyAsString();
                if (!raw.isBlank()) {
                    var trimmed = raw.trim();
                    // If it looks like JSON, forward it directly
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        return ResponseEntity.status(HttpStatus.CONFLICT)
                                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                                .body(trimmed);
                    }
                }
            } catch (Exception ignore) { }

            // Fallback: return a simple JSON structure compatible with the UI handler
            Map<String, Object> body = new HashMap<>();
            body.put("status", "error");
            body.put("messages", java.util.List.of("User with this email already exists"));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        }
        nukeAllCookies(request, response);
        return ResponseEntity.status(HttpStatus.SEE_OTHER) // 303 -> GET /
                .header(HttpHeaders.LOCATION, "/")
                .build();
    }

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<?> handleMissingRequestCookie(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        nukeAllCookies(request, response);
        return ResponseEntity.status(HttpStatus.SEE_OTHER) // 303 -> GET /
                .header(HttpHeaders.LOCATION, "/")
                .build();
    }

    @ExceptionHandler({ MethodArgumentNotValidException.class, BindException.class })
    public ResponseEntity<?> handleValidation(Exception ex) {
        var br = (ex instanceof MethodArgumentNotValidException manve)
                ? manve.getBindingResult()
                : ((BindException) ex).getBindingResult();

        var fieldErrors = br.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (a, b) -> a // keep first
                ));

        // include object-level (global) errors too, if you use them
        var globalErrors = br.getGlobalErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.toList());

        var body = new HashMap<>();
        body.put("status", "error");
        body.put("errors", fieldErrors);
        if (!globalErrors.isEmpty()) body.put("globalErrors", globalErrors);

        return ResponseEntity.badRequest().body(body);
    }

    private void nukeAllCookies(HttpServletRequest request,
                                HttpServletResponse response) {
        // Nuke all cookies the client sent
        AuthController.nukeAllCookies(request, response);
    }
}
