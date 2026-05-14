package com.mobiscroll.connect.sample;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mobiscroll.connect")
public class SdkProperties {

    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String baseUrl;

    public String getClientId() { return clientId; }
    public void setClientId(String v) { this.clientId = v; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String v) { this.clientSecret = v; }

    public String getRedirectUri() { return redirectUri; }
    public void setRedirectUri(String v) { this.redirectUri = v; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String v) { this.baseUrl = v; }
}
