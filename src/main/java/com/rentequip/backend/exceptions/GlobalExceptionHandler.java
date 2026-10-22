package com.rentequip.backend.exceptions;

import com.rentequip.backend.dtos.response.ErrorResponse;
import com.rentequip.backend.dtos.response.ErrorResponse.FieldValidationError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Single place where every exception becomes an ErrorResponse, so clients always parse the same shape.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RentEquipException.class)
    public ResponseEntity<ErrorResponse> handleDomainException(RentEquipException exception,
                                                              HttpServletRequest request) {
        log.warn("Domain error [{}] on {}: {}", exception.getErrorCode(), request.getRequestURI(),
                exception.getMessage());
        return build(exception.getStatus(), exception.getErrorCode(), exception.getMessage(), request, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleBodyValidation(MethodArgumentNotValidException exception,
                                                             HttpServletRequest request) {
        List<FieldValidationError> details = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldValidationError)
                .sorted(Comparator.comparing(FieldValidationError::field))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "One or more fields are invalid", request, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleParameterValidation(ConstraintViolationException exception,
                                                                  HttpServletRequest request) {
        List<FieldValidationError> details = exception.getConstraintViolations().stream()
                .map(this::toFieldValidationError)
                .sorted(Comparator.comparing(FieldValidationError::field))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "One or more parameters are invalid", request, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST_BODY",
                "The request body is missing or is not valid JSON", request, null);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParameter(MissingServletRequestParameterException exception,
                                                                HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required parameter " + exception.getParameterName() + " is missing", request, null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(MissingRequestHeaderException exception,
                                                             HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_HEADER",
                "Required header " + exception.getHeaderName() + " is missing", request, null);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                            HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH",
                "Parameter " + exception.getName() + " has an invalid value", request, null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrity(DataIntegrityViolationException exception,
                                                             HttpServletRequest request) {
        log.warn("Data integrity violation on {}", request.getRequestURI(), exception);
        return build(HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                "The operation conflicts with an existing record", request, null);
    }

    @ExceptionHandler({PessimisticLockingFailureException.class, OptimisticLockingFailureException.class})
    public ResponseEntity<ErrorResponse> handleLockFailure(Exception exception, HttpServletRequest request) {
        log.warn("Lock acquisition failed on {}", request.getRequestURI(), exception);
        return build(HttpStatus.CONFLICT, "CONCURRENT_REQUEST",
                "The resource is being modified by another request, please retry", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException exception,
                                                            HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You are not allowed to perform this action",
                request, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                                  HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                "This endpoint does not support " + exception.getMethod(), request, null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException exception,
                                                          HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "ENDPOINT_NOT_FOUND", "No endpoint matches this request", request, null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException exception,
                                                             HttpServletRequest request) {
        return build(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE", "The file must be 5 MB or smaller", request, null);
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(MissingServletRequestPartException exception,
                                                          HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
                "Required part '" + exception.getRequestPartName() + "' is missing", request, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), exception);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred, please contact support", request, null);
    }

    private FieldValidationError toFieldValidationError(FieldError fieldError) {
        return new FieldValidationError(fieldError.getField(), fieldError.getRejectedValue(),
                fieldError.getDefaultMessage());
    }

    private FieldValidationError toFieldValidationError(ConstraintViolation<?> violation) {
        return new FieldValidationError(violation.getPropertyPath().toString(), violation.getInvalidValue(),
                violation.getMessage());
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                HttpServletRequest request, List<FieldValidationError> details) {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                request.getRequestURI(),
                details
        );
        return ResponseEntity.status(status).body(body);
    }
}
