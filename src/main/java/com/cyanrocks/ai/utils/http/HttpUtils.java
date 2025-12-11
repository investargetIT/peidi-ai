package com.cyanrocks.ai.utils.http;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Map;

/**
 *
 */
public class HttpUtils {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtils.class);

    public static HttpClientContext initHttpClientContext(Proxy proxy, HttpTimeoutConfig config) {
        return initHttpClientContext(proxy, config, null);

    }

    public static HttpClientContext initHttpClientContext(Proxy proxy, HttpTimeoutConfig config, String cookieSpec) {
        if (cookieSpec == null) {
            cookieSpec = CookieSpecs.DEFAULT;
        }
        HttpClientContext httpClientContext = new HttpClientContext();
        if (config == null) {
            config = new HttpTimeoutConfig();
        }
        if (proxy != null) {
            HttpHost httpHost = new HttpHost(proxy.getHost(), proxy.getPort(), "http");
            if (StringUtils.isNotBlank(proxy.getUsername())) {
                CredentialsProvider credsProvider = new BasicCredentialsProvider();
                credsProvider.setCredentials(new AuthScope(httpHost),
                    new UsernamePasswordCredentials(proxy.getUsername(), proxy.getPassword()));
                httpClientContext.setCredentialsProvider(credsProvider);
            }
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(config.getConnectTimeout())
                .setConnectionRequestTimeout(config.getConnectRequestTimeout()).setSocketTimeout(config.getSoTimeout())
                .setCookieSpec(cookieSpec).setStaleConnectionCheckEnabled(true).setProxy(httpHost).build();
            httpClientContext.setRequestConfig(requestConfig);

        } else {
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(config.getConnectTimeout())
                .setConnectionRequestTimeout(config.getConnectRequestTimeout()).setSocketTimeout(config.getSoTimeout())
                .setCookieSpec(cookieSpec).setStaleConnectionCheckEnabled(true).build();
            httpClientContext.setRequestConfig(requestConfig);
        }

        return httpClientContext;

    }

    public static HttpResponseContent get(String url, Map<String, String> headers, HttpClientContext context,
                                          boolean isIngoreCert) {
        CloseableHttpClient hc = null;
        try {
            hc = getCloseableHttpClient(isIngoreCert);
            if (logger.isInfoEnabled()) {
                logger.info("url:" + url + ",headers:" + headers + ",cookie=" + context.getCookieStore());
            }
            HttpGet httpGet = new HttpGet(url);
            if (headers != null && !headers.isEmpty()) {
                headers.entrySet().forEach(entry -> {
                    httpGet.addHeader(entry.getKey(), entry.getValue());
                });
            }
            long start = System.currentTimeMillis();
            CloseableHttpResponse response = hc.execute(httpGet, context);
            HttpEntity responseEntity = response.getEntity();
            HttpResponseContent content = new HttpResponseContent();
            content.setStatusCode(response.getStatusLine().getStatusCode());
            if (responseEntity != null) {
                content.setContent(EntityUtils.toString(responseEntity));
            }
            long cost = System.currentTimeMillis() - start;
            if (logger.isInfoEnabled()) {
                logger.info("url:" + url + ",cost:" + cost + " ms");
            }
            return content;
        } catch (Exception e) {
            String proxy = null;
            if (context != null && context.getRequestConfig() != null
                && context.getRequestConfig().getProxy() != null) {
                proxy = context.getRequestConfig().getProxy().toHostString();
            }
            throw new RuntimeException("request exception,request=" + url + ",proxy=" + proxy, e);
        } finally {
            try {
                hc.close();
            } catch (IOException e) {
                logger.error("CloseableHttpClient close error");
            }
        }
    }

    private static CloseableHttpClient getCloseableHttpClient(boolean isIngoreCert) throws Exception {
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (isIngoreCert) {
            // 在调用SSL之前需要重写验证方法，取消检测SSL
            X509TrustManager trustManager = new X509TrustManager() {
                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkClientTrusted(X509Certificate[] xcs, String str) {}

                @Override
                public void checkServerTrusted(X509Certificate[] xcs, String str) {}
            };
            SSLContext ctx = SSLContext.getInstance(SSLConnectionSocketFactory.TLS);
            ctx.init(null, new TrustManager[] {trustManager}, null);
            SSLConnectionSocketFactory socketFactory
                = new SSLConnectionSocketFactory(ctx, NoopHostnameVerifier.INSTANCE);
            httpClientBuilder.setSSLSocketFactory(socketFactory);
        }
        httpClientBuilder.setRetryHandler(new DefaultHttpRequestRetryHandler());
        httpClientBuilder.setDefaultRequestConfig(RequestConfig.custom().setConnectTimeout(3000)
            .setConnectionRequestTimeout(5000).setSocketTimeout(100000).setStaleConnectionCheckEnabled(true).build());
        return httpClientBuilder.build();
    }

}
