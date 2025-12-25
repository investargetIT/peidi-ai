package com.cyanrocks.ai.log;

import cn.hutool.json.JSONUtil;
import com.cyanrocks.ai.dao.entity.ApiLog;
import com.cyanrocks.ai.dao.mapper.ApiLogMapper;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;

/**
 * @Author wjq
 * @Date 2025/9/22 15:19
 */
@Aspect
@Component
public class ApiLogAspect {

    @Autowired
    private ApiLogMapper apiLogMapper;

    @Around("@annotation(log)")
    public Object log(ProceedingJoinPoint joinPoint, Log log) throws Throwable {
        // 执行原方法
        Object result = joinPoint.proceed();
        ApiLog apiLog = new ApiLog();

        try {
            // 获取请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                return result;
            }
            HttpServletRequest request = attributes.getRequest();

            // 设置日志信息
            apiLog.setUserId(request.getAttribute("userId") != null ? request.getAttribute("userId").toString() : "unknown");
            apiLog.setUserName(request.getAttribute("userName") != null ? request.getAttribute("userName").toString() : "unknown");
            apiLog.setRequestUri(request.getRequestURI());
            apiLog.setOperation(log.value());
            apiLog.setTime(LocalDateTime.now());

            if (log.logParams()) {
                apiLog.setParams(getRequestParams(joinPoint));
            }

            if (log.logResult()) {
                apiLog.setResult(JSONUtil.toJsonStr(result));
            }

        } catch (Exception e) {
            apiLog.setResult("error");
        } finally {
            // 保存日志（实际项目中可保存到数据库）
            apiLogMapper.insert(apiLog);
        }

        return result;
    }


    private String getRequestParams(ProceedingJoinPoint joinPoint) {
        // 获取方法参数信息
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof MultipartFile) {
                return "";
            }
        }
        // 转换为JSON字符串
        return JSONUtil.toJsonStr(args);
    }
}