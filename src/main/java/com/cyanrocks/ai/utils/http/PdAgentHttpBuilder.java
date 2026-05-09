package com.cyanrocks.ai.utils.http;

import cn.hutool.core.util.StrUtil;
import com.cyanrocks.ai.config.PdAgentConfig;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 基于 PdAgentConfig 的 HTTP 请求构建器
 * 支持 builder 模式构建请求
 */
public class PdAgentHttpBuilder {

    private static final Logger logger = LoggerFactory.getLogger(PdAgentHttpBuilder.class);
    private static final MediaType JSON_MEDIA_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final PdAgentConfig config;
    private final OkHttpClient client;

    private String path;
    private final Map<String, String> headers = new HashMap<>();
    private final Map<String, String> queryParams = new HashMap<>();
    private String requestBody;
    private Integer timeoutMs;

    private PdAgentHttpBuilder(PdAgentConfig config) {
        this.config = config;
        this.timeoutMs = parseTimeout(config.getTimeoutMs());
        this.client = new OkHttpClient.Builder()
                .connectTimeout(this.timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(this.timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(this.timeoutMs, TimeUnit.MILLISECONDS)
                .retryOnConnectionFailure(true)
                .build();
    }

    public static PdAgentHttpBuilder create(PdAgentConfig config) {
        return new PdAgentHttpBuilder(config);
    }

    private Integer parseTimeout(String timeoutMs) {
        try {
            return StrUtil.isNotBlank(timeoutMs) ? Integer.parseInt(timeoutMs) : 30000;
        } catch (NumberFormatException e) {
            return 30000;
        }
    }

    public PdAgentHttpBuilder path(String path) {
        this.path = path;
        return this;
    }

    public PdAgentHttpBuilder header(String name, String value) {
        this.headers.put(name, value);
        return this;
    }

    public PdAgentHttpBuilder headers(Map<String, String> headers) {
        this.headers.putAll(headers);
        return this;
    }

    public PdAgentHttpBuilder queryParam(String name, String value) {
        this.queryParams.put(name, value);
        return this;
    }

    public PdAgentHttpBuilder queryParams(Map<String, String> params) {
        this.queryParams.putAll(params);
        return this;
    }

    public PdAgentHttpBuilder body(String requestBody) {
        this.requestBody = requestBody;
        return this;
    }

    public PdAgentHttpBuilder timeout(int timeoutMs) {
        this.timeoutMs = timeoutMs;
        return this;
    }

    private HttpUrl buildUrl() {
        String baseUrl = config.getAgentUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String fullPath = StrUtil.isBlank(path) ? "" : (path.startsWith("/") ? path : "/" + path);

        HttpUrl.Builder urlBuilder = HttpUrl.parse(baseUrl + fullPath).newBuilder();

        queryParams.forEach(urlBuilder::addQueryParameter);
        System.out.println(baseUrl + fullPath);
        return urlBuilder.build();
    }

    private Request.Builder buildRequestBuilder() {
        Request.Builder builder = new Request.Builder();
        headers.forEach(builder::addHeader);
        return builder;
    }

    public String get() {
        HttpUrl url = buildUrl();
        Request.Builder requestBuilder = buildRequestBuilder()
                .url(url)
                .get();

        return execute(requestBuilder.build());
    }

    public String post() {
        HttpUrl url = buildUrl();
        RequestBody body = requestBody != null
                ? RequestBody.create(requestBody, JSON_MEDIA_TYPE)
                : RequestBody.create("", JSON_MEDIA_TYPE);

        Request.Builder requestBuilder = buildRequestBuilder()
                .url(url)
                .post(body);

        return execute(requestBuilder.build());
    }

    public String postForm(Map<String, String> formData) {
        HttpUrl url = buildUrl();
        FormBody.Builder formBuilder = new FormBody.Builder(StandardCharsets.UTF_8);
        formData.forEach(formBuilder::add);

        Request.Builder requestBuilder = buildRequestBuilder()
                .url(url)
                .post(formBuilder.build());

        return execute(requestBuilder.build());
    }

    private String execute(Request request) {
        long start = System.currentTimeMillis();
        try (Response response = client.newCall(request).execute()) {
            long cost = System.currentTimeMillis() - start;
            logger.info("请求 url={}, 耗时={} ms, 状态码={}", request.url(), cost, response.code());

            ResponseBody responseBody = response.body();
            if (!response.isSuccessful()) {
                String errorBody = responseBody != null ? responseBody.string() : "无响应体";
                throw new RuntimeException("HTTP请求失败: " + response.code() + ", body=" + errorBody);
            }
            return responseBody != null ? responseBody.string() : null;
        } catch (IOException e) {
            long cost = System.currentTimeMillis() - start;
            logger.error("请求异常 url={}, 耗时={} ms", request.url(), cost, e);
            throw new RuntimeException("HTTP请求异常: " + e.getMessage(), e);
        }
    }

    public Response getResponse() throws IOException {
        HttpUrl url = buildUrl();
        Request.Builder requestBuilder = buildRequestBuilder()
                .url(url)
                .get();
        return client.newCall(requestBuilder.build()).execute();
    }

    public Response postResponse() throws IOException {
        HttpUrl url = buildUrl();
        RequestBody body = requestBody != null
                ? RequestBody.create(requestBody, JSON_MEDIA_TYPE)
                : RequestBody.create("", JSON_MEDIA_TYPE);

        Request.Builder requestBuilder = buildRequestBuilder()
                .url(url)
                .post(body);
        return client.newCall(requestBuilder.build()).execute();
    }
}