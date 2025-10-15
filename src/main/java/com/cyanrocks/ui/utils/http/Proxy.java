package com.cyanrocks.ui.utils.http;


/**
 *
 */
public class Proxy extends ReflectionToString {
    private String proxyType;
    private String host;
    private int port;
    private String username;
    private String password;

    public Proxy(String proxyType, String host, int port) {
        this.proxyType = proxyType;
        this.host = host;
        this.port = port;
    }

    public Proxy(String proxyType, String host, int port, String username, String password) {
        this.proxyType = proxyType;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;

        Proxy proxy = (Proxy)o;
        if (port != proxy.port)
            return false;
        return host != null ? !host.equals(proxy.host) : proxy.host != null;
    }

    @Override
    public int hashCode() {
        int result = host != null ? host.hashCode() : 0;
        result = 31 * result + port;
        return result;
    }

}
