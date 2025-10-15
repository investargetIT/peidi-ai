package com.cyanrocks.ui.exception;


import com.cyanrocks.ui.constants.GlobalLogEnum;
import com.cyanrocks.ui.utils.LogUtils;
import com.cyanrocks.ui.vo.response.GenericResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

/**
 * @Description
 * @Author yaoshunyu
 * @Date 2021-10-12 18:00
 * @Version 1.0
 **/
@ControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({BusinessException.class})
    @ResponseBody
    public GenericResponse<Void> handleBusinessException(BusinessException e, HttpServletRequest request) {
        return GenericResponse.bizError(e.getCode(), e.getMessage(), null);
    }

    /**
     * 自定义参数校验注解的异常捕获
     */
    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseBody
    public GenericResponse<Void> handleValidException(MethodArgumentNotValidException e, HttpServletRequest request) {
        writeLogForError(e, request);
        // 获取用户定义的message
        // String msg = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return GenericResponse.paramIllegal(null);
    }

    @ExceptionHandler({Throwable.class})
    @ResponseBody
    public GenericResponse<Void> handleNotDefinedException(Throwable e, HttpServletRequest request) {
        writeLogForError(e, request);
        return GenericResponse.error();
    }

    private void writeLogForError(Throwable e, HttpServletRequest request) {
        LogUtils.alertError(GlobalLogEnum.GLOBAL_ERROR.getKey(), String.format("unKnow error Exception occurred while processing request: %s %s", request.getMethod(),
                request.getRequestURI()), e);
    }
}
