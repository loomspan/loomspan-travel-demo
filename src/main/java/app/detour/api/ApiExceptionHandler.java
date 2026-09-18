package app.detour.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> apiException(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(new ApiError(exception.code(), exception.getMessage(), exception.fields()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(ApiError.of("MALFORMED_REQUEST", "Request body is not valid JSON."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> missing(NoResourceFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RESOURCE_NOT_FOUND", "The requested resource was not found."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("INTERNAL_ERROR", "The request could not be completed."));
    }
}
