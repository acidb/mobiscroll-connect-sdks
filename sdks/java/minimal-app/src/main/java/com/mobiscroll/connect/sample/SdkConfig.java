package com.mobiscroll.connect.sample;

import com.mobiscroll.connect.MobiscrollConnectClient;
import com.mobiscroll.connect.MobiscrollConnectConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SdkProperties.class)
public class SdkConfig {

    @Bean
    public MobiscrollConnectClient mobiscrollConnectClient(SdkProperties props) {
        MobiscrollConnectConfig.Builder b = MobiscrollConnectConfig.builder()
                .clientId(props.getClientId())
                .clientSecret(props.getClientSecret())
                .redirectUri(props.getRedirectUri());
        if (props.getBaseUrl() != null && !props.getBaseUrl().isEmpty()) {
            b.baseUrl(props.getBaseUrl());
        }
        return new MobiscrollConnectClient(b.build());
    }
}
