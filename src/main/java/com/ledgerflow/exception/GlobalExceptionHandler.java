package com.ledgerflow.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.ledgerflow.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException exception, HttpServletRequest request) {
        return respond(exception.getStatus(), exception.getCode(), exception.getMessage(), exception.getDetails(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage())
                .distinct()
                .toList();
        if (details.isEmpty()) {
            details = List.of("Request validation failed");
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", details, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException exception, HttpServletRequest request) {
        if (invalidTransactionType(exception)) {
            return respond(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION_TYPE", "Invalid transaction type",
                    List.of("type must be CREDIT or DEBIT"), request);
        }
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body is invalid", bodyDetails(exception), request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
        String name = exception.getName() == null ? "" : exception.getName();
        if ("page".equals(name) || "size".equals(name)) {
            return respond(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "Invalid pagination parameters",
                    List.of(name + " must be an integer"), request);
        }
        if ("type".equals(name)) {
            return respond(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION_TYPE", "Invalid transaction type",
                    List.of("type must be CREDIT or DEBIT"), request);
        }
        if (name.toLowerCase(Locale.ROOT).contains("account")) {
            return respond(HttpStatus.BAD_REQUEST, "INVALID_IDENTIFIER", "Invalid identifier",
                    List.of(name + " must be a valid UUID"), request);
        }
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed",
                List.of("A request parameter has an invalid value"), request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleIntegrity(DataIntegrityViolationException exception, HttpServletRequest request) {
        log.warn("Data constraint rejected a write on {}", request.getRequestURI());
        return integrityResponse(exception, request);
    }

    @ExceptionHandler(TransactionSystemException.class)
    public ResponseEntity<ErrorResponse> handleTransaction(TransactionSystemException exception, HttpServletRequest request) {
        Throwable cause = exception.getRootCause();
        if (cause instanceof ApiException apiException) {
            return handleApi(apiException, request);
        }
        if (cause instanceof DataIntegrityViolationException integrityException) {
            return handleIntegrity(integrityException, request);
        }
        return handleUnexpected(exception, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleMissingResource(NoResourceFoundException exception, HttpServletRequest request) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", List.of(), request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethod(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method is not supported",
                List.of("This resource cannot be edited or deleted"), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMedia(HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported media type",
                List.of("Content-Type must be application/json"), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), exception);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred",
                List.of(), request);
    }

    private ResponseEntity<ErrorResponse> integrityResponse(DataIntegrityViolationException exception, HttpServletRequest request) {
        String blob = (exception.getMostSpecificCause().getMessage() == null
                ? ""
                : exception.getMostSpecificCause().getMessage()).toLowerCase(Locale.ROOT);
        if (blob.contains("uk_transfers_reference")) {
            return respond(HttpStatus.CONFLICT, "DUPLICATE_REFERENCE", "Transfer reference already exists",
                    List.of("A transfer with this referenceId already exists"), request);
        }
        if (blob.contains("uk_txn_account_reference")) {
            return respond(HttpStatus.CONFLICT, "DUPLICATE_REFERENCE", "Transaction reference already exists",
                    List.of("A transaction with this referenceId already exists for the account"), request);
        }
        if (blob.contains("ck_accounts_balance_non_negative")) {
            return respond(HttpStatus.CONFLICT, "INSUFFICIENT_FUNDS", "Insufficient funds",
                    List.of("Account balance cannot become negative"), request);
        }
        return respond(HttpStatus.BAD_REQUEST, "DATA_CONSTRAINT", "Request could not be stored",
                List.of("A data constraint was violated"), request);
    }

    private boolean invalidTransactionType(HttpMessageNotReadableException exception) {
        Throwable cursor = exception;
        while (cursor != null) {
            if (cursor instanceof InvalidFormatException invalid
                    && invalid.getTargetType() != null
                    && invalid.getTargetType().isEnum()) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private List<String> bodyDetails(HttpMessageNotReadableException exception) {
        Throwable cursor = exception;
        while (cursor != null) {
            if (cursor instanceof InvalidFormatException invalid && invalid.getTargetType() != null) {
                Class<?> target = invalid.getTargetType();
                if (target.isEnum()) {
                    return List.of("type must be CREDIT or DEBIT");
                }
                if (target == UUID.class) {
                    return List.of("Identifiers must be valid UUIDs");
                }
                if (target == BigDecimal.class) {
                    return List.of("amount must be a decimal number");
                }
            }
            cursor = cursor.getCause();
        }
        return List.of("Request body is malformed or contains an invalid value");
    }

    private ResponseEntity<ErrorResponse> respond(
            HttpStatus status,
            String code,
            String message,
            List<String> details,
            HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                clock.instant(),
                status.value(),
                status.getReasonPhrase(),
                code,
                message,
                details,
                request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
