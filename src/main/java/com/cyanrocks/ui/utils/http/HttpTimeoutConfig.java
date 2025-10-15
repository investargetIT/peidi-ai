package com.cyanrocks.ui.utils.http;

/**
 *
 */
public class HttpTimeoutConfig {

    private int connectTimeout = 2000;
    private int soTimeout = 20000;
    private int connectRequestTimeout = 3000;

    public HttpTimeoutConfig() {}

    public HttpTimeoutConfig(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public void setSoTimeout(int soTimeout) {
        this.soTimeout = soTimeout;
    }

    public int getConnectRequestTimeout() {
        return connectRequestTimeout;
    }

    public void setConnectRequestTimeout(int connectRequestTimeout) {
        this.connectRequestTimeout = connectRequestTimeout;
    }
}
