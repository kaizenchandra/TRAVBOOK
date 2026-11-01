package com.travbook.platform.security;

import java.net.URI;

/** Startup policy only; authentication, token validation and ownership remain in their existing boundaries. */
final class IdentityEnvironmentPolicy {
    private IdentityEnvironmentPolicy() {}

    static void validate(String issuer, boolean loginEnabled, boolean localIdentity, String origin,
                         String bindAddress, boolean secureCookie, boolean composeLocal, String mode) {
        if (composeLocal && (!loginEnabled || !localIdentity || !"MOCK".equals(mode)
            || !LocalComposeIdentityConfiguration.ISSUER.equals(issuer)
            || !LocalComposeIdentityConfiguration.ORIGIN.equals(origin)
            || !"0.0.0.0".equals(bindAddress) || secureCookie)) {
            throw new IllegalStateException("compose-local requires the fixed localhost MOCK fixture topology.");
        }
        if (!issuer.isBlank() && !issuer.startsWith("https://")
            && !(localIdentity && "127.0.0.1".equals(URI.create(issuer).getHost()))) {
            throw new IllegalStateException("OIDC requires HTTPS except explicit loopback local identity.");
        }
        if (loginEnabled) {
            URI web = URI.create(origin);
            if (web.getUserInfo()!=null || web.getQuery()!=null || web.getFragment()!=null
                || !(web.getPath()==null || web.getPath().isEmpty())) {
                throw new IllegalStateException("WEB_ORIGIN must be an origin without a path or query.");
            }
            if (localIdentity) {
                if (!"127.0.0.1".equals(web.getHost()) || (!composeLocal && !"127.0.0.1".equals(bindAddress))) {
                    throw new IllegalStateException("Local OIDC must bind to loopback outside compose-local.");
                }
            } else if (!"https".equals(web.getScheme()) || !secureCookie) {
                throw new IllegalStateException("OIDC sessions require HTTPS and Secure cookies.");
            }
            if (issuer.isBlank()) throw new IllegalStateException("OIDC issuer is required for login.");
        }
    }
}
