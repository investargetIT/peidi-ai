package com.cyanrocks.ui.interceptor;

import com.cyanrocks.ui.utils.http.HttpClientService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @Author wjq
 * @Date 2024/8/14 16:12
 */
@Component
public class LoginInterceptor implements HandlerInterceptor {

    private final HttpClientService httpClientService;

    public LoginInterceptor(HttpClientService httpClientService) {
        this.httpClientService = httpClientService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

//        String uri = request.getRequestURI();
//
//        String authorization;
//        if (null != request.getParameter("authorization")){
//            authorization = request.getParameter("authorization");
//        }else {
//            authorization = request.getHeader("Authorization");
//        }
//        // 在请求到达控制器之前进行处理
//        String url = "https://user.peidigroup.cn/user/user-check?token=" + authorization;
//        HttpResponseContent content;
//        try {
//            content = httpClientService.doGet(url, null, null,
//                    HttpUtils.initHttpClientContext(null, new HttpTimeoutConfig(300000)));
//        } catch (Exception e) {
//            return false;
//        }
//        if (200 != content.getStatusCode()) {
//            return false;
//        }
//        JSONObject contentJson = JSONUtil.parseObj(content.getContent());
//        if (ErrorCodeEnum.SESSION_INVALID.getCode().equals(contentJson.getInt("code"))){
//            throw new BusinessException(ErrorCodeEnum.SESSION_INVALID.getCode(), "token 无效");
//        }
//        if (ErrorCodeEnum.SESSION_EXPIRED.getCode().equals(contentJson.getInt("code"))){
//            throw new BusinessException(ErrorCodeEnum.SESSION_INVALID.getCode(), "token 过期");
//        }
//        System.out.println("Pre-handle: " + request.getRequestURI());
        return true; // 返回 true 继续请求处理，返回 false 终止请求
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        // 在控制器处理完之后进行处理，但在视图渲染之前
//        System.out.println("Post-handle: " + request.getRequestURI());
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 请求完成后处理
//        System.out.println("After completion: " + request.getRequestURI());
    }
}