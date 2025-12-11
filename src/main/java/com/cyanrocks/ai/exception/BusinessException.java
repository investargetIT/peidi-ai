package com.cyanrocks.ai.exception;

/**
 * @Description
 * @Author yaoshunyu
 * @Date 2021-10-12 17:53
 * @Version 1.0
 **/
public class BusinessException extends RuntimeException {
    private int code;

    private Object data;

    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }
}
