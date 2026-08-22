package com.dividendanatomy.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * 서비스 계층 예외를 HTTP 상태로 변환. 스택트레이스나 내부 예외 클래스명은
 * 노출하지 않고, 이미 명확하게 작성된 예외 메시지만 그대로 전달한다.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleUnprocessable(IllegalStateException e) {
        // Spring 7.0부터 UNPROCESSABLE_ENTITY가 UNPROCESSABLE_CONTENT로 이름만 바뀜 (RFC 9110 용어에 맞춤, 상태 코드는 그대로 422)
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT).body(new ErrorResponse(e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(e.getMessage()));
    }
}
