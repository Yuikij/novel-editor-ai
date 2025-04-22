package com.soukon.core.http;

import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.List;

@Data
public class ApiResponse<T> {

    private int code;

    private String msg;

    private Boolean success;

    private T data;
    private List<T> list;

    public ApiResponse() {
    }

    public ApiResponse(int code, String msg, T data, Boolean success) {
        this.code = code;
        this.msg = msg;
        this.data = data;
        this.success = success;
    }

    public ApiResponse<T> list(List<T> list) {
        this.list = list;
        return this;
    }

    public static <T> ApiResponse<T> success() {
        return ApiResponse.success("操作成功");
    }

    /**
     * 返回成功数据
     *
     * @return 成功消息
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.success("操作成功", data);
    }

    /**
     * 返回成功消息
     *
     * @param msg 返回内容
     * @return 成功消息
     */
    public static <T> ApiResponse<T> success(String msg) {
        return ApiResponse.success(msg, null);
    }

    /**
     * 返回成功消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 成功消息
     */
    public static <T> ApiResponse<T> success(String msg, T data) {
        return new ApiResponse<T>(HttpStatus.OK.value(), msg, data, true);
    }

    /**
     * 返回错误消息
     *
     * @param code 状态码
     * @param msg  返回内容
     * @return 警告消息
     */
    public static <T> ApiResponse<T> success(int code, String msg) {
        return new ApiResponse<T>(code, msg, null, true);
    }


    /**
     * 返回错误消息
     *
     * @return
     */
    public static <T> ApiResponse<T> error() {
        return ApiResponse.error("操作失败");
    }

    /**
     * 返回错误消息
     *
     * @param msg 返回内容
     * @return 警告消息
     */
    public static <T> ApiResponse<T> error(String msg) {
        return ApiResponse.error(msg, null);
    }

    /**
     * 返回错误消息
     *
     * @param msg  返回内容
     * @param data 数据对象
     * @return 警告消息
     */
    public static <T> ApiResponse<T> error(String msg, T data) {
        return new ApiResponse<T>(HttpStatus.INTERNAL_SERVER_ERROR.value(), msg, data, false);
    }

    /**
     * 返回错误消息
     *
     * @param code 状态码
     * @param msg  返回内容
     * @return 警告消息
     */
    public static <T> ApiResponse<T> error(int code, String msg) {
        return new ApiResponse<T>(code, msg, null, false);
    }
}
