package com.cyanrocks.ai.config;

import cn.hutool.json.JSONUtil;
import com.cyanrocks.ai.vo.response.GenericResponse;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * @Author wjq
 * @Date 2024/8/6 13:52
 */
@RestControllerAdvice
public class GlobalResponseFormatter implements ResponseBodyAdvice<Object> {
    @Override
    public boolean supports(MethodParameter methodParameter, Class<? extends HttpMessageConverter<?>> aClass) {

        return filter(methodParameter);
    }

    @Override
    public Object beforeBodyWrite(Object o, MethodParameter methodParameter, MediaType mediaType,
                                  Class<? extends HttpMessageConverter<?>> aClass, ServerHttpRequest serverHttpRequest,
                                  ServerHttpResponse serverHttpResponse) {
        GenericResponse<Object> genericResponse = GenericResponse.success(o);
        // 处理字符串时，遇到了类型转换的问题，debug一步一步跟踪，原来是对于字符串的ContentType是“text-plain”，
        // ConverterType是StringHttpMessageConverter这个类型转换，
        if (o == null) {
            // 创建data为null的成功响应对象，并转换为JSON字符串
            return GenericResponse.success(null);
        }
        // 由于将结果封装成了自定义的GenericResponse类型，所以有GenericResponse转换成String报错
        // 所以需要对String类型的返回值单独进行处理
        if (o instanceof String) {
            return JSONUtil.toJsonStr(genericResponse);
        }
        // 处理 byte[] 类型
        if (o instanceof byte[]) {
            // 直接返回 byte[]，不进行封装
            return o;
        }

        return GenericResponse.success(o);
    }

    private Boolean filter(MethodParameter methodParameter) {
        Class<?> declaringClass = methodParameter.getDeclaringClass();
        // swagger中的所有返回不进行统一封装
        if (declaringClass.getName().contains("springfox.documentation")) {
            return false;
        }
        if (methodParameter.hasMethodAnnotation(ExceptionHandler.class)) {
            return false;
        }
        // 如果本身就是使用GenericResponse返回，则不需要进行格式化
        return !methodParameter.getGenericParameterType().equals(GenericResponse.class);
    }
}
