package com.cyanrocks.ai.utils.http;

import cn.hutool.core.util.StrUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class HttpClientService {

    @Autowired
    @Qualifier("defaultHttpClient")
    private CloseableHttpClient httpClient;

    private static final Logger logger = LoggerFactory.getLogger(HttpClientService.class);

    public HttpResponseContent doGet(String url, Map<String, String> headers, HttpClientContext localContext) {
        if (logger.isDebugEnabled()) {
            logger.debug("url:" + url + ",headers:" + headers);
        }
        long start = System.currentTimeMillis();
        HttpGet httpGet = new HttpGet(url);
        if (headers != null && !headers.isEmpty()) {
            headers.entrySet().forEach(entry -> {
                httpGet.addHeader(entry.getKey(), entry.getValue());
            });
        }
        HttpResponseContent result = execute(httpGet, localContext);
        long cost = System.currentTimeMillis() - start;
        if (logger.isInfoEnabled()) {
            logger.info("url:" + url + ",cost:" + cost + " ms");
        }
        return result;
    }

    public HttpResponseContent doGet(String url, Map<String, String> headers, Map<String, String> params,
        HttpClientContext context) {
        HttpGet httpGet;
        try {
            URIBuilder uriBuilder = new URIBuilder(url);
            uriBuilder.setCharset(StandardCharsets.UTF_8).build();
            if (params != null) {
                params.forEach(uriBuilder::addParameter);
            }
            httpGet = new HttpGet(uriBuilder.build());
            if (headers != null && !headers.isEmpty()) {
                headers.forEach(httpGet::addHeader);
            }
        } catch (Exception e) {
            throw new RuntimeException("非法的url: " + url);
        }
        return doHttp(httpGet, context);
    }

    /**
     * 实际上调用远程的方法
     *
     * @param request httpGet/httpPost的共同父类
     */
    private HttpResponseContent doHttp(HttpRequestBase request, HttpClientContext context) {
        HttpResponseContent responseContent = new HttpResponseContent();
        CloseableHttpClient executeClient;
        executeClient = httpClient;
        try {
            long startPoint = System.currentTimeMillis();
            CloseableHttpResponse response = executeClient.execute(request, context);
            logger.info("请求url =【{}】, 请求headers =【{}】, 请求耗时 = 【{} ms】", request.getURI(), request.getAllHeaders(),
                System.currentTimeMillis() - startPoint);
            HttpEntity responseEntity = response.getEntity();
            responseContent.setStatusCode(response.getStatusLine().getStatusCode());
            if (responseEntity != null) {
                responseContent.setContent(EntityUtils.toString(responseEntity));
            }
            if (logger.isDebugEnabled()) {
                logger.debug("请求url【{}, 请求返回结果【{} ms】, ", request.getURI(), responseContent.getContent());
            }
        } catch (Exception e) {
            throw new RuntimeException("request exception,request=" + request, e);
        }
        return responseContent;
    }

    public HttpEntity doPostReturnStream(String url, Map<String, String> headers, String jsonBody,
        HttpClientContext localContext) {
        if (logger.isDebugEnabled()) {
            logger.debug("请求url={}, 入参={}", url, jsonBody);
        }
        HttpPost httpPost = new HttpPost(url);
        if (headers != null && !headers.isEmpty()) {
            headers.forEach(httpPost::addHeader);
        }
        if (StrUtil.isNotBlank(jsonBody)) {
            StringEntity requestEntity = new StringEntity(jsonBody, StandardCharsets.UTF_8);
            requestEntity.setContentEncoding(StandardCharsets.UTF_8.name());
            httpPost.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE);
            httpPost.setEntity(requestEntity);
        }
        try {
            CloseableHttpResponse response = httpClient.execute(httpPost, localContext);
            return response.getEntity();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public HttpResponseContent doPost(String url, Map<String, String> headers, String jsonBody,
        HttpClientContext localContext) {
        if (logger.isDebugEnabled()) {
            logger.debug("url:" + url + ",headers:" + headers + ",jsonBody:" + jsonBody);
        }
        long start = System.currentTimeMillis();
        HttpPost httpPost = new HttpPost(url);
        if (headers != null && !headers.isEmpty()) {
            headers.entrySet().forEach(entry -> {
                httpPost.addHeader(entry.getKey(), entry.getValue());
            });
        }
        if (StringUtils.isNotBlank(jsonBody)) {
            StringEntity requestEntity = new StringEntity(jsonBody, "utf-8");
            requestEntity.setContentEncoding("UTF-8");
            httpPost.setHeader("Content-type", "application/json");
            httpPost.setEntity(requestEntity);
        }
        HttpResponseContent result = execute(httpPost, localContext);
        long cost = System.currentTimeMillis() - start;
        if (logger.isInfoEnabled()) {
            logger.info("url:" + url + ",cost:" + cost + " ms");
        }
        return result;
    }

    private HttpResponseContent execute(HttpRequestBase request, HttpClientContext localContext) {

        CloseableHttpResponse response = null;
        try {
            response = httpClient.execute(request, localContext);
            HttpEntity responseEntity = response.getEntity();
            HttpResponseContent content = new HttpResponseContent();
            content.setStatusCode(response.getStatusLine().getStatusCode());
            if (responseEntity != null) {
                content.setContent(EntityUtils.toString(responseEntity));
            }
            return content;
        } catch (Exception e) {
            String proxy = null;
            if (localContext != null && localContext.getRequestConfig() != null
                && localContext.getRequestConfig().getProxy() != null) {
                proxy = localContext.getRequestConfig().getProxy().toHostString();
            }
            throw new RuntimeException("request exception,request=" + request + ",proxy=" + proxy, e);
        }

    }

    public String sendRequest(String requestUrl, String body) throws IOException
    {
        PrintWriter outWriter = null;
        BufferedReader inReader = null;
        String responseBody;
        try
        {
            URL url = new URL(requestUrl);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);

            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Version-SDK", "Java-3.0");
            conn.setDoOutput(true);
            conn.setDoInput(true);

            OutputStream outStream = conn.getOutputStream();
            outWriter = new PrintWriter(new OutputStreamWriter(outStream, StandardCharsets.UTF_8));

            outWriter.write(body);
            outWriter.flush();

            InputStream in = conn.getInputStream();
            inReader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));

            StringBuilder sb = new StringBuilder();
            int len;
            char[] tmp = new char[256];
            while ((len = inReader.read(tmp)) > 0)
            {
                sb.append(tmp, 0, len);
            }
            responseBody = sb.toString();
        }
        finally
        {
            if (outWriter != null)
                outWriter.close();

            if (inReader != null)
                inReader.close();
        }
        return responseBody;
    }

}