package com.cyanrocks.ui.utils.http;

import org.apache.http.client.CookieStore;

public class HttpResponseContent {

    private String content;

    private int statusCode;

    private CookieStore cookieStore;

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    public void setCookieStore(CookieStore cookieStore) {
        this.cookieStore = cookieStore;
    }

}
