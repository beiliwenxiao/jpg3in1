package com.framework.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

/**
 * TLS/SSL 配置
 * 
 * 支持配置 TLS 证书，启用加密通信。
 * 
 * 需求: 11.1
 */
public class TlsConfig {

    private static final Logger logger = LoggerFactory.getLogger(TlsConfig.class);

    private boolean enabled;
    private String certFile;
    private String keyFile;
    private String caFile;
    private String keystorePath;
    private String keystorePassword;
    private String truststorePath;
    private String truststorePassword;
    private String protocol = "TLSv1.3";

    public TlsConfig() {
        this.enabled = false;
    }

    /**
     * 创建 SSLContext
     */
    public SSLContext createSslContext() throws Exception {
        if (!enabled) {
            return SSLContext.getDefault();
        }

        KeyManager[] keyManagers = null;
        TrustManager[] trustManagers = null;

        // 加载 KeyStore（服务端证书）
        if (keystorePath != null) {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileInputStream(keystorePath)) {
                keyStore.load(is, keystorePassword != null ? keystorePassword.toCharArray() : null);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword != null ? keystorePassword.toCharArray() : null);
            keyManagers = kmf.getKeyManagers();
        }

        // 加载 TrustStore（CA 证书）
        if (truststorePath != null) {
            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream is = new FileInputStream(truststorePath)) {
                trustStore.load(is, truststorePassword != null ? truststorePassword.toCharArray() : null);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            trustManagers = tmf.getTrustManagers();
        }

        SSLContext sslContext = SSLContext.getInstance(protocol);
        sslContext.init(keyManagers, trustManagers, null);

        logger.info("TLS 已配置: protocol={}", protocol);
        return sslContext;
    }

    // Builder pattern
    public static TlsConfig enabled() {
        TlsConfig config = new TlsConfig();
        config.enabled = true;
        return config;
    }

    public static TlsConfig disabled() {
        return new TlsConfig();
    }

    public TlsConfig withKeystore(String path, String password) {
        this.keystorePath = path;
        this.keystorePassword = password;
        return this;
    }

    public TlsConfig withTruststore(String path, String password) {
        this.truststorePath = path;
        this.truststorePassword = password;
        return this;
    }

    public TlsConfig withCertFiles(String certFile, String keyFile, String caFile) {
        this.certFile = certFile;
        this.keyFile = keyFile;
        this.caFile = caFile;
        return this;
    }

    public TlsConfig withProtocol(String protocol) {
        this.protocol = protocol;
        return this;
    }

    public boolean isEnabled() { return enabled; }
    public String getCertFile() { return certFile; }
    public String getKeyFile() { return keyFile; }
    public String getCaFile() { return caFile; }
    public String getProtocol() { return protocol; }
}
