package com.ultracards.server.controllers.errors;

import com.ultracards.gateway.dto.admin.AdminApiErrorDTO;
import com.ultracards.server.entity.UserEntity;
import com.ultracards.server.service.admin.AdminAuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice(basePackages = "com.ultracards.server.controllers")
public class ApiExceptionHandler {
    @Autowired(required = false)
    private AdminAuditService adminAuditService;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<AdminApiErrorDTO> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                          HttpServletRequest request) {
        var errors = new LinkedHashMap<String, String>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), resolveMessage(fieldError));
        }

        var globalErrors = ex.getBindingResult().getGlobalErrors().stream()
                .map(this::resolveMessage)
                .toList();

        auditRejectedMutation(request, "Validation failure");
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failure",
                errors,
                globalErrors
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<AdminApiErrorDTO> handleHandlerMethodValidation(HandlerMethodValidationException ex,
                                                                          HttpServletRequest request) {
        var errors = ex.getParameterValidationResults().stream()
                .filter(result -> !result.getResolvableErrors().isEmpty())
                .collect(Collectors.toMap(
                        this::resolveParameterName,
                        result -> resolveMessage(result.getResolvableErrors().getFirst()),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        var globalErrors = ex.getCrossParameterValidationResults().stream()
                .map(this::resolveMessage)
                .toList();

        auditRejectedMutation(request, "Validation failure");
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Validation failure",
                errors,
                globalErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<AdminApiErrorDTO> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                          HttpServletRequest request) {
        auditRejectedMutation(request, "Bad request body");
        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad request body",
                Map.of(),
                List.of()
        );
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<AdminApiErrorDTO> handleResponseStatusException(ResponseStatusException ex,
                                                                           HttpServletRequest request) {
        var reason = ex.getReason();
        var message = (reason == null || reason.isBlank()) ? "Request failed" : reason;
        auditRejectedMutation(request, message);
        return buildResponse(
                ex.getStatusCode(),
                message,
                Map.of(),
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<AdminApiErrorDTO> handleUnhandledException(Exception ex) {
        log.error("Unhandled API exception", ex);
        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal server error",
                Map.of(),
                List.of()
        );
    }

    private ResponseEntity<AdminApiErrorDTO> buildResponse(
            HttpStatusCode status,
            String message,
            Map<String, String> errors,
            List<String> globalErrors
    ) {
        var body = new AdminApiErrorDTO(
                status.value(),
                message,
                errors,
                globalErrors
        );
        return ResponseEntity.status(status).body(body);
    }

    private String resolveParameterName(ParameterValidationResult result) {
        var methodParameter = result.getMethodParameter();
        var parameterName = methodParameter.getParameterName();
        if (parameterName != null && !parameterName.isBlank()) {
            return parameterName;
        }
        return "arg" + methodParameter.getParameterIndex();
    }

    private String resolveMessage(MessageSourceResolvable resolvable) {
        var defaultMessage = resolvable.getDefaultMessage();
        if (defaultMessage != null && !defaultMessage.isBlank()) {
            return defaultMessage;
        }
        return "Invalid value";
    }

    private void auditRejectedMutation(HttpServletRequest request, String safeReason) {
        if (adminAuditService == null || request == null || "GET".equals(request.getMethod())
                || !request.getRequestURI().startsWith("/api/admin/v1/")) return;
        try {
            var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            var principal = authentication == null ? null : authentication.getPrincipal();
            var actorId = principal instanceof UserEntity user ? user.getId() : null;
            adminAuditService.record(actorId, "REJECTED_" + request.getMethod(), "ADMIN_API",
                    request.getRequestURI(), null, safeReason, "REJECTED");
        } catch (RuntimeException auditFailure) {
            log.warn("Could not record rejected admin mutation", auditFailure);
        }
    }

}
