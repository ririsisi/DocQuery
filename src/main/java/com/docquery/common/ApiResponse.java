package com.docquery.common;

/**
 * 通用API响应对象
 * @param <T> 响应数据类型
 */
public record ApiResponse<T>(
    int status,
    String message,
    T data
) {
    /**
     * 返回成功响应，包含数据
     * @param data 响应数据
     * @return ApiResponse<T>
     */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "Success", data);
    }

    /**
     * 返回失败响应，包含错误码和错误信息
     * @param status 状态码
     * @param message 错误信息
     * @return ApiResponse<T>
     */
    public static <T> ApiResponse<T> fail(int status, String message) {
        return new ApiResponse<>(status, message, null);
    }
}
