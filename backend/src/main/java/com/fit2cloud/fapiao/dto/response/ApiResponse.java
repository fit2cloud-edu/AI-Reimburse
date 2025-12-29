package com.fit2cloud.fapiao.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Integer code;

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, "success", data, 200);
    }

    // 新增：支持自定义消息的成功响应
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, 200);
    }

    // 新增：只有消息的成功响应
    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, 200);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, 500);
    }

    // 新增：支持自定义错误码的错误响应
    public static <T> ApiResponse<T> error(String message, Integer code) {
        return new ApiResponse<>(false, message, null, code);
    }
}