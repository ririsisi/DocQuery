package com.docquery.common;

/** 接口统一包一层，避免各 Controller 各写一套 JSON。 */
public record ApiResponse<T>(
    int status,
    String message,
    T data
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "Success", data);
    }

    public static <T> ApiResponse<T> fail(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }
}
