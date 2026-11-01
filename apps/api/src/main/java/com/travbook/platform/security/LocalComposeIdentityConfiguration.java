package com.travbook.platform.security;

import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import java.util.Map;

/** Fixed local fixture topology. Never discover the public loopback URL from a container. */
@Configuration
@Profile("compose-local")
public class LocalComposeIdentityConfiguration {
    static final String ISSUER = "http://127.0.0.1:8180/realms/travbook";
    static final String ORIGIN = "http://127.0.0.1:3000";
    static final String INTERNAL = "http://identity:8180/realms/travbook/protocol/openid-connect";
    static final String JWK_SET = INTERNAL + "/certs";

    @Bean ClientRegistrationRepository localComposeClient() {
        return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("travbook")
            .clientId("travbook-web")
            .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri(ORIGIN + "/login/oauth2/code/travbook")
            .scope("openid", "quotes:read", "quotes:write", "bookings:read", "bookings:write")
            .issuerUri(ISSUER)
            .authorizationUri(ISSUER + "/protocol/openid-connect/auth")
            .tokenUri(INTERNAL + "/token")
            .jwkSetUri(JWK_SET)
            .userInfoUri(INTERNAL + "/userinfo")
            .userNameAttributeName("sub")
            .providerConfigurationMetadata(Map.of("end_session_endpoint", ISSUER + "/protocol/openid-connect/logout"))
            .clientName("Travbook local Compose identity")
            .build());
    }
}
