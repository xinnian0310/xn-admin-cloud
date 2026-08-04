package com.smartadmin.common;

import com.smartadmin.service.ExceptionLogService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ObjectProvider<ExceptionLogService> exceptionLogServiceProvider;

    public GlobalExceptionHandler(ObjectProvider<ExceptionLogService> exceptionLogServiceProvider) {
        this.exceptionLogServiceProvider = exceptionLogServiceProvider;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        HttpStatus status =
                switch (ex.getCode()) {
                    case 403 -> HttpStatus.FORBIDDEN;
                    case 423 -> HttpStatus.LOCKED;
                    case 429 -> HttpStatus.TOO_MANY_REQUESTS;
                    default -> HttpStatus.BAD_REQUEST;
                };
        return ResponseEntity.status(status).body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        return ApiResponse.error(400, message);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ApiResponse<Void> handleException(Exception ex) {
        exceptionLogServiceProvider.ifAvailable(service -> service.record(ex));
        return ApiResponse.error(500, "服务器内部错误: " + ex.getMessage());
    }
}
