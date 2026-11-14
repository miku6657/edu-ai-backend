package com.edu.ai.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DuplicateKeyException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;

import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // 业务异常
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Object>> handleBusiness(
            BusinessException e) {

        int code = e.getCode();

        HttpStatus status = HttpStatus.resolve(code);

        if (status == null || code < 400 || code >= 600) {
            status = HttpStatus.BAD_REQUEST;
            code = 400;
        }

        return ResponseEntity
                .status(status)
                .body(Result.error(code, e.getMessage()));
    }

    // 数据库唯一键冲突
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<Result<Object>> handleDuplicateKey(
            DuplicateKeyException e) {

        log.warn("数据库唯一键冲突: {}",
                e.getMostSpecificCause().getMessage());

        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(Result.error(
                        409,
                        "请求数据发生冲突，请检查后重试"
                ));
    }

    // 资源不存在
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Object>> handleNoResourceFound(
            NoResourceFoundException ex) {

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(Result.error(
                        404,
                        "请求的资源不存在"
                ));
    }

    // 请求参数校验异常
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Object>> handleValidation(
            MethodArgumentNotValidException e) {

        String message = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .findFirst()
                .map(error -> error.getDefaultMessage())
                .orElse("请求参数不合法");

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(Result.error(400, message));
    }

    // 用户登录认证失败
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Object>> handleAuthentication(
            AuthenticationException e) {

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(Result.error(
                        401, "用户名或密码错误"));
    }

    // 方法级权限拒绝
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Object>> handleAccessDenied(
            AccessDeniedException e) {

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(Result.error(
                        403, "没有访问权限"));
    }

    // 未知服务器异常
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Object>> handleException(
            Exception e) {

        log.error("服务器异常", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Result.error(
                        500, "服务器内部错误"));
    }
}
