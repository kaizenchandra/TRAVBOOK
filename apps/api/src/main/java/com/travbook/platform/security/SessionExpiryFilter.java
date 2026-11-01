package com.travbook.platform.security;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import java.io.IOException;
import java.time.Instant;
final class SessionExpiryFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if(auth!=null && auth.getPrincipal() instanceof OidcUser user) {
            Instant expiry=user.getIdToken().getExpiresAt();
            if(expiry==null || !expiry.isAfter(Instant.now())) {
                if(request.getSession(false)!=null)request.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request,response);
    }
}

