package com.travbook.platform.security;
import com.travbook.shared.domain.CustomerIdentity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
public final class AuthenticatedIdentity {
    private AuthenticatedIdentity() {}
    public static CustomerIdentity from(Authentication authentication) {
        if(authentication!=null && authentication.isAuthenticated()) {
            if(authentication.getPrincipal() instanceof Jwt jwt) return new CustomerIdentity(jwt.getIssuer().toString(),jwt.getSubject());
            if(authentication.getPrincipal() instanceof OidcUser oidc) return new CustomerIdentity(oidc.getIssuer().toString(),oidc.getSubject());
        }
        throw new AuthenticationCredentialsNotFoundException("Verified customer authentication required");
    }
}

