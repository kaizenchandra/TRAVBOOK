package com.travbook.platform.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.*;

class IdentityEnvironmentPolicyTest {
    private static final String ISSUER=LocalComposeIdentityConfiguration.ISSUER;
    private static final String ORIGIN=LocalComposeIdentityConfiguration.ORIGIN;

    @Test void explicitComposeFixtureIsAcceptedWithSeparatePublicAndBackchannelEndpoints() {
        assertThatCode(()->IdentityEnvironmentPolicy.validate(ISSUER,true,true,ORIGIN,"0.0.0.0",false,true,"MOCK"))
            .doesNotThrowAnyException();
        var client=new LocalComposeIdentityConfiguration().localComposeClient().findByRegistrationId("travbook");
        assertThat(client.getProviderDetails().getIssuerUri()).isEqualTo(ISSUER);
        assertThat(client.getProviderDetails().getAuthorizationUri()).startsWith(ISSUER);
        assertThat(client.getProviderDetails().getTokenUri()).startsWith("http://identity:8180/");
        assertThat(client.getProviderDetails().getJwkSetUri()).isEqualTo(LocalComposeIdentityConfiguration.JWK_SET);
        assertThat(client.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint")).isEqualTo(ISSUER+"/protocol/openid-connect/logout");
        assertThat(client.getRedirectUri()).isEqualTo(ORIGIN+"/login/oauth2/code/travbook");
    }

    @ParameterizedTest
    @CsvSource({
        "MOCK,true,true,http://127.0.0.1:3000,0.0.0.0,false,http://other:8180/realms/travbook",
        "LIVE,true,true,http://127.0.0.1:3000,0.0.0.0,false,http://127.0.0.1:8180/realms/travbook",
        "SANDBOX,true,true,http://127.0.0.1:3000,0.0.0.0,false,http://127.0.0.1:8180/realms/travbook",
        "MOCK,false,true,http://127.0.0.1:3000,0.0.0.0,false,http://127.0.0.1:8180/realms/travbook",
        "MOCK,true,false,http://127.0.0.1:3000,0.0.0.0,false,http://127.0.0.1:8180/realms/travbook",
        "MOCK,true,true,http://example.com:3000,0.0.0.0,false,http://127.0.0.1:8180/realms/travbook",
        "MOCK,true,true,http://127.0.0.1:3000,127.0.0.1,false,http://127.0.0.1:8180/realms/travbook",
        "MOCK,true,true,http://127.0.0.1:3000,0.0.0.0,true,http://127.0.0.1:8180/realms/travbook"
    })
    void composeRejectsMixedModesOrChangedTrustTopology(String mode,boolean login,boolean local,String origin,
                                                      String bind,boolean secure,String issuer) {
        assertThatThrownBy(()->IdentityEnvironmentPolicy.validate(issuer,login,local,origin,bind,secure,true,mode))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("fixed localhost MOCK fixture");
    }

    @Test void nativeLocalIdentityStillCannotBindPublicly() {
        assertThatThrownBy(()->IdentityEnvironmentPolicy.validate(ISSUER,true,true,ORIGIN,"0.0.0.0",false,false,"MOCK"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("loopback");
        assertThatCode(()->IdentityEnvironmentPolicy.validate(ISSUER,true,true,ORIGIN,"127.0.0.1",false,false,"MOCK"))
            .doesNotThrowAnyException();
    }

    @Test void externalIdentityStillRequiresHttpsAndSecureCookies() {
        assertThatThrownBy(()->IdentityEnvironmentPolicy.validate(ISSUER,true,false,ORIGIN,"0.0.0.0",false,false,"MOCK"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("HTTPS");
        assertThatThrownBy(()->IdentityEnvironmentPolicy.validate("https://identity.example",true,false,"https://travel.example","0.0.0.0",false,false,"MOCK"))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("Secure cookies");
        assertThatCode(()->IdentityEnvironmentPolicy.validate("https://identity.example",true,false,"https://travel.example","0.0.0.0",true,false,"MOCK"))
            .doesNotThrowAnyException();
    }
}
