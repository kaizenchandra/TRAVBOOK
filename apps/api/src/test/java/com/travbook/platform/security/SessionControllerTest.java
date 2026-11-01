package com.travbook.platform.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;

class SessionControllerTest {
    @Test void loginRedirectUsesValidatedBrowserOriginInsteadOfInternalOrSpoofedHost() throws Exception {
        var request=new MockHttpServletRequest();
        request.setServerName("backend");request.setServerPort(8080);
        request.addHeader("Forwarded","host=attacker.example;proto=https");
        request.addHeader("X-Forwarded-Host","attacker.example");
        var response=new MockHttpServletResponse();
        new SessionController(true,true,"http://127.0.0.1:3000").login(null,null,null,null,null,null,request,response);
        assertThat(response.getRedirectedUrl()).isEqualTo("http://127.0.0.1:3000/oauth2/authorization/travbook");
    }
}
