package dev.vedaaxis.api.common;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import dev.vedaaxis.api.plan.RuleValidationException;
import dev.vedaaxis.api.rule.RuleValidationResult;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiError(
                exception.code(), exception.getMessage(), List.of(), Instant.now()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        List<ApiError.FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_FAILED", "请求字段校验失败", violations, Instant.now()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraintValidation(ConstraintViolationException exception) {
        List<ApiError.FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(item -> new ApiError.FieldViolation(item.getPropertyPath().toString(), item.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_FAILED", "请求参数校验失败", violations, Instant.now()));
    }

    @ExceptionHandler(RuleValidationException.class)
    ResponseEntity<RuleValidationResult> handleRuleValidation(RuleValidationException exception) {
        return ResponseEntity.unprocessableEntity().body(exception.validation());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                "INTERNAL_ERROR", "服务暂时无法处理该请求", List.of(), Instant.now()));
    }

    private ApiError.FieldViolation toViolation(FieldError error) {
        return new ApiError.FieldViolation(error.getField(), error.getDefaultMessage());
    }
}
