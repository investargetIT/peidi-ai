package com.cyanrocks.ai.vo.response;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;

/**
 * @Author wjq
 * @Date 2024/8/6 13:52
 */
public class GenericResponse<T> {

    public static final int SUCCESS_CODE = 200;

    public static final String SUCCESS_MSG = "success";

    public static final int ERROR_CODE = 500;

    public static final String ERROR_MSG = "Internal server error";

    public static final int PARAM_ILLEGAL_CODE = 400;

    public static final String PARAM_ILLEGAL_CODE_MSG = "parameter is invalid";

    private int code;
    private String msg;
    private boolean success;
    private T data;

    private GenericResponse(int code, String msg, boolean success, T data) {
        this.code = code;
        this.msg = msg;
        this.success = success;
        this.data = data;
    }

    public static <T> GenericResponse<T> success(T data) {
        return new GenericResponse<>(SUCCESS_CODE, SUCCESS_MSG, true, data);
    }

    public static GenericResponse<Void> success() {
        return new GenericResponse<>(SUCCESS_CODE, SUCCESS_MSG, true, null);
    }

    public static GenericResponse<Void> paramIllegal(String errMasg) {
        if (StringUtils.isBlank(errMasg)) {
            errMasg = PARAM_ILLEGAL_CODE_MSG;
        }
        return new GenericResponse<>(PARAM_ILLEGAL_CODE, errMasg, false, null);
    }

    public static <T> GenericResponse<T> paramIllegal(T data) {
        return new GenericResponse<>(PARAM_ILLEGAL_CODE, PARAM_ILLEGAL_CODE_MSG, false, data);
    }

    public static <T> GenericResponse<T> error(T data) {
        return new GenericResponse<>(ERROR_CODE, ERROR_MSG, false, data);
    }

    public static <T> GenericResponse<T> error(String errorMsg, T data) {
        return new GenericResponse<>(ERROR_CODE, errorMsg, false, data);
    }

    public static GenericResponse<Void> error() {
        return new GenericResponse<>(ERROR_CODE, ERROR_MSG, false, null);
    }

    public static <T> GenericResponse<T> bizError(int errorCode, String errorMsg, T data) {
        return new GenericResponse<>(errorCode, errorMsg, false, data);
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }
}