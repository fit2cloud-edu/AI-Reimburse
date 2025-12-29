package com.fit2cloud.fapiao.exception;

import com.fit2cloud.fapiao.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MultipartException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ApiResponse.error(e.getMessage());
    }

    @ExceptionHandler(MultipartException.class)
    public ApiResponse<?> handleMultipartException(MultipartException e) {
        log.error("文件上传异常", e);
        return ApiResponse.error("文件上传失败，请检查文件大小和格式");
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<?> handleException(Exception e) {
        log.error("系统异常", e);
        return ApiResponse.error("系统繁忙，请稍后重试");
    }
}
