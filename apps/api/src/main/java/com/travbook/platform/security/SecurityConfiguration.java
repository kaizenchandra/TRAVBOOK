package com.travbook.platform.security;

import jakarta.servlet.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.core.env.Environment;

@Configuration
public class SecurityConfiguration {
    @Bean SecurityFilterChain security(HttpSecurity http,
        @Value("${travbook.oidc-issuer}") String issuer,
        @Value("${travbook.oidc-audience}") String audience,
        @Value("${travbook.login-enabled}") boolean loginEnabled,
        @Value("${travbook.local-identity}") boolean localIdentity,
        @Value("${travbook.web-origin}") String origin,
        @Value("${server.address}") String bindAddress,
        @Value("${server.servlet.session.cookie.secure}") boolean secureCookie,
        @Value("${travbook.integration-mode}") String mode,
        Environment environment,
        ObjectProvider<ClientRegistrationRepository> registrations) throws Exception {
        boolean composeLocal=environment.matchesProfiles("compose-local");
        if(composeLocal && environment.matchesProfiles("oidc", "local-oidc"))
            throw new IllegalStateException("compose-local cannot be combined with other identity profiles.");
        IdentityEnvironmentPolicy.validate(issuer,loginEnabled,localIdentity,origin,bindAddress,secureCookie,composeLocal,mode);
        http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
            .requestCache(c->c.requestCache(new NullRequestCache()))
            .csrf(c->c.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/api/v1/flights/search","/api/v1/flights/offers/*/reprice")
                .ignoringRequestMatchers("/api/v1/hotels/search","/api/v1/hotels/offers/*/reprice")
                .ignoringRequestMatchers("/api/v1/trains/search","/api/v1/buses/search","/api/v1/buses/trips/*/reprice","/api/v1/cabs/search","/api/v1/cabs/offers/*/reprice")
                .ignoringRequestMatchers(r->r.getHeader("Authorization")!=null && r.getHeader("Authorization").regionMatches(true,0,"Bearer ",0,7)))
            .authorizeHttpRequests(a->a
                .requestMatchers(HttpMethod.GET,"/api/v1/me/notifications").authenticated()
                .requestMatchers(HttpMethod.PUT,"/api/v1/me/notifications/*/read").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/coupons","/api/v1/demo/flight-provider/quotes/*/promotion").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v2/demo/flight-provider/quotes").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v1/demo/flight-provider/reservations").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.GET,"/api/v1/me/travel-organizer").authenticated()
                .requestMatchers(HttpMethod.PUT,"/api/v1/me/travel-organizer").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/demo/flight-provider/capabilities","/api/v1/demo/flight-provider/flights","/api/v1/demo/flight-provider/flights/*/seats","/api/v1/demo/flight-provider/flights/*/ancillaries","/api/v1/demo/flight-provider/quotes/*","/api/v1/demo/flight-provider/reservations/*","/api/v1/demo/flight-provider/reservations/*/documents").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v1/demo/flight-provider/quotes","/api/v1/demo/flight-provider/reservations","/api/v1/demo/flight-provider/reservations/*/payments","/api/v1/demo/flight-provider/reservations/*/tickets","/api/v1/demo/flight-provider/reservations/*/check-in","/api/v1/demo/flight-provider/reservations/*/cancellations","/api/v1/demo/flight-provider/reservations/*/refunds").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.POST,"/api/v1/demo/coupons/preview").hasAuthority("SCOPE_quotes:read")
                .requestMatchers(HttpMethod.GET,"/api/v1/demo/gps/*","/api/v1/demo/payments/*","/api/v1/demo/financial-report").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.GET,"/api/v1/demo/catalog","/api/v1/demo/deployment","/api/v1/demo/operations","/api/v1/demo/operations/*","/api/v1/demo/privacy-preview").authenticated()
                .requestMatchers(HttpMethod.POST,"/api/v1/demo/operations").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/me/profile").authenticated()
                .requestMatchers(HttpMethod.PUT,"/api/v1/me/profile").authenticated()
                .requestMatchers(HttpMethod.GET,"/auth/session","/auth/login","/oauth2/authorization/*","/login/oauth2/code/*").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/locations","/api/v1/locations/*","/api/v1/flights/searches/*","/api/v1/flights/offers/*","/actuator/health","/actuator/health/*").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/flights/search","/api/v1/flights/offers/*/reprice").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/hotel-searches/*","/api/v1/hotels/*","/api/v1/hotels/*/rooms").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/hotels/search","/api/v1/hotels/offers/*/reprice").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/railway-stations","/api/v1/train-searches/*","/api/v1/trains/*","/api/v1/trains/*/availability").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/trains/search").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/stay-quotes/*","/api/v1/stay-bookings/*","/api/v1/stay-bookings/*/voucher","/api/v1/stay-bookings/*/voucher/content","/api/v1/stay-bookings/*/timeline","/api/v1/me/stay-bookings").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v1/stay-quotes","/api/v1/stay-quotes/*/acceptance","/api/v1/stay-bookings","/api/v1/stay-bookings/*/cancellation-quotes","/api/v1/stay-bookings/*/cancellations","/api/v1/stay-bookings/*/voucher").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v2/stay-quotes/*","/api/v2/stay-bookings/*","/api/v2/stay-bookings/*/voucher","/api/v2/stay-bookings/*/voucher/content","/api/v2/stay-bookings/*/timeline","/api/v2/me/stay-bookings").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v2/stay-quotes","/api/v2/stay-quotes/*/acceptance","/api/v2/stay-bookings","/api/v2/stay-bookings/*/cancellation-quotes","/api/v2/stay-bookings/*/cancellations","/api/v2/stay-bookings/*/voucher","/api/v2/stay-bookings/*/amendments").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v3/stay-quotes/*","/api/v3/stay-bookings/*","/api/v3/stay-bookings/*/voucher","/api/v3/stay-bookings/*/voucher/content","/api/v3/stay-bookings/*/timeline","/api/v3/me/stay-bookings").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v3/stay-quotes","/api/v3/stay-quotes/*/acceptance","/api/v3/stay-bookings","/api/v3/stay-bookings/*/cancellation-quotes","/api/v3/stay-bookings/*/cancellations","/api/v3/stay-bookings/*/voucher","/api/v3/stay-bookings/*/change-quotes","/api/v3/stay-bookings/*/changes").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v1/rail-ticket-quotes/*","/api/v1/rail-ticket-bookings/*","/api/v1/rail-ticket-bookings/*/voucher","/api/v1/rail-ticket-bookings/*/voucher/content","/api/v1/rail-ticket-bookings/*/timeline","/api/v1/me/rail-ticket-bookings","/api/v1/rail-ticket-pnr/*").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v1/rail-ticket-quotes","/api/v1/rail-ticket-quotes/*/acceptance","/api/v1/rail-ticket-bookings","/api/v1/rail-ticket-bookings/*/cancellation-quotes","/api/v1/rail-ticket-bookings/*/cancellations","/api/v1/rail-ticket-bookings/*/voucher").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v2/rail-ticket-bookings/*","/api/v2/rail-ticket-bookings/*/voucher","/api/v2/rail-ticket-bookings/*/voucher/content","/api/v2/rail-ticket-pnr/*").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v2/rail-ticket-bookings/*/cancellation-quotes","/api/v2/rail-ticket-bookings/*/cancellations","/api/v2/rail-ticket-bookings/*/voucher").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v1/bus-searches/*","/api/v1/buses/trips/*","/api/v1/buses/trips/*/seats","/api/v1/cab-searches/*","/api/v1/cabs/offers/*","/api/v1/holidays/packages","/api/v1/holidays/packages/*").permitAll()
                .requestMatchers(HttpMethod.POST,"/api/v1/buses/search","/api/v1/buses/trips/*/reprice","/api/v1/cabs/search","/api/v1/cabs/offers/*/reprice").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/v1/holidays/quotes/*","/api/v1/holidays/enquiries/*").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v1/holidays/quotes","/api/v1/holidays/enquiries").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v1/journey-quotes/*","/api/v1/journey-bookings/*","/api/v1/journey-bookings/*/document","/api/v1/me/journey-bookings").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v1/journey-quotes","/api/v1/journey-quotes/*/acceptance","/api/v1/journey-bookings","/api/v1/journey-bookings/*/payment","/api/v1/journey-bookings/*/cancellation-quotes","/api/v1/journey-bookings/*/cancellations").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v2/journey-quotes/*","/api/v2/journey-bookings/*","/api/v2/journey-bookings/*/document","/api/v2/journey-bookings/*/dispatch","/api/v2/me/journey-bookings").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v2/journey-quotes","/api/v2/journey-quotes/*/acceptance","/api/v2/journey-bookings","/api/v2/journey-bookings/*/payment","/api/v2/journey-bookings/*/balance","/api/v2/journey-bookings/*/cancellation-quotes","/api/v2/journey-bookings/*/cancellations","/api/v2/journey-bookings/*/reschedule-quotes","/api/v2/journey-bookings/*/reschedules","/api/v2/journey-bookings/*/dispatch-demo").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v1/me").authenticated()
                .requestMatchers(HttpMethod.GET,"/api/v1/quotes/*").hasAuthority("SCOPE_quotes:read")
                .requestMatchers(HttpMethod.POST,"/api/v1/quotes","/api/v1/quotes/*/acceptance").hasAuthority("SCOPE_quotes:write")
                .requestMatchers(HttpMethod.GET,"/api/v1/quotes/*/booking-requirements").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v1/bookings/*","/api/v1/bookings/*/timeline","/api/v1/me/bookings").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v1/bookings").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v1/bookings/*/documents","/api/v1/bookings/*/documents/*/content").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v1/bookings/*/documents").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.POST,"/api/v1/bookings/*/cancellation-quotes","/api/v1/bookings/*/cancellations").hasAuthority("SCOPE_bookings:write")
                .requestMatchers(HttpMethod.GET,"/api/v1/bookings/*/refunds").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.GET,"/api/v2/bookings/*","/api/v2/me/bookings").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.GET,"/api/v3/bookings/*","/api/v3/me/bookings").hasAuthority("SCOPE_bookings:read")
                .requestMatchers(HttpMethod.POST,"/api/v2/bookings/*/checkout").hasAuthority("SCOPE_bookings:write")
                .requestMatchers("/api/v1/operations/**","/api/v2/operations/**").access((authentication,context)->new org.springframework.security.authorization.AuthorizationDecision(OperatorIdentity.allowed(authentication.get())))
                .anyRequest().denyAll())
            .headers(h->h.contentSecurityPolicy(c->c.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
            .exceptionHandling(e->e.authenticationEntryPoint((req,res,ex)->problem(req,res,401,"UNAUTHENTICATED","Sign in to continue."))
                .accessDeniedHandler((req,res,ex)->problem(req,res,403,"FORBIDDEN","Access is not permitted or the security token has expired. Refresh your session and retry.")))
            .addFilterAfter(new SessionExpiryFilter(),SecurityContextHolderFilter.class);
        if(!issuer.isBlank()) {
            NimbusJwtDecoder decoder=composeLocal
                ? NimbusJwtDecoder.withJwkSetUri(LocalComposeIdentityConfiguration.JWK_SET).build()
                : JwtDecoders.fromIssuerLocation(issuer);
            OAuth2TokenValidator<Jwt> claims=jwt->jwt.getAudience().contains(audience) && jwt.getExpiresAt()!=null && jwt.getSubject()!=null && !jwt.getSubject().isBlank()
                ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token","Required access-token claims missing",null));
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer),claims));
            http.oauth2ResourceServer(o->o.jwt(j->j.decoder(decoder))
                .authenticationEntryPoint((req,res,ex)->problem(req,res,401,"UNAUTHENTICATED","The access token is invalid or expired.")));
        }
        if(loginEnabled) {
            ClientRegistrationRepository clients=registrations.getIfAvailable();
            if(clients==null) throw new IllegalStateException("Configure the travbook OAuth2 client before enabling login.");
            var resolver=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
            resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
            http.oauth2Login(o->o.authorizationEndpoint(a->a.authorizationRequestResolver(resolver))
                .authorizedClientRepository(new DiscardAuthorizedTokens())
                .successHandler((request,response,authentication)->{
                    Object offer=request.getSession().getAttribute("quoteReturnOffer");
                    request.getSession().removeAttribute("quoteReturnOffer");
                    Object intent=request.getSession().getAttribute("quoteReturnIntent");
                    request.getSession().removeAttribute("quoteReturnIntent");
                    Object quote=request.getSession().getAttribute("quoteReturnId");
                    request.getSession().removeAttribute("quoteReturnId");
                    Object booking=request.getSession().getAttribute("bookingReturnId");
                    Object bookingQuote=request.getSession().getAttribute("bookingReturnQuoteId");
                    Object stay=request.getSession().getAttribute("stayReturn");request.getSession().removeAttribute("stayReturn");
                    if(stay instanceof String path && StayReturnPath.valid(path)){response.sendRedirect(origin+path);return;}
                    request.getSession().removeAttribute("bookingReturnId");request.getSession().removeAttribute("bookingReturnQuoteId");
                    response.sendRedirect(origin+(booking instanceof java.util.UUID?"/bookings/"+booking:bookingQuote instanceof java.util.UUID?"/bookings/new?quoteId="+bookingQuote+(intent instanceof java.util.UUID?"&requestId="+intent:""):quote instanceof java.util.UUID?"/quotes/"+quote:offer instanceof java.util.UUID ? "/quotes/new?offerId="+offer+(intent instanceof java.util.UUID?"&requestId="+intent:"") : "/account"));
                }).failureHandler((request,response,error)->{
                    if(request.getSession(false)!=null)request.getSession(false).invalidate();
                    response.sendRedirect(origin+"/account?loginError=true");
                }));
            var logout=new OidcClientInitiatedLogoutSuccessHandler(clients);
            logout.setPostLogoutRedirectUri(origin+"/account?loggedOut=true");
            http.logout(l->l.logoutUrl("/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("TRAVBOOK_SESSION").logoutSuccessHandler(logout));
        }
        return http.build();
    }
    static void problem(HttpServletRequest req,HttpServletResponse res,int status,String code,String detail) throws java.io.IOException {
        res.setStatus(status); res.setContentType("application/problem+json");
        res.getWriter().write("{\"type\":\"urn:travbook:problem:"+code.toLowerCase(java.util.Locale.ROOT)+"\",\"title\":\""+code+"\",\"status\":"+status+",\"detail\":\""+detail+"\",\"code\":\""+code+"\",\"correlationId\":\""+req.getAttribute("correlationId")+"\"}");
    }
    private static final class DiscardAuthorizedTokens implements OAuth2AuthorizedClientRepository {
        public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String id,Authentication principal,HttpServletRequest request) { return null; }
        public void saveAuthorizedClient(OAuth2AuthorizedClient client,Authentication principal,HttpServletRequest request,HttpServletResponse response) { /* No provider calls after login: do not retain access/refresh tokens. */ }
        public void removeAuthorizedClient(String id,Authentication principal,HttpServletRequest request,HttpServletResponse response) {}
    }
}
