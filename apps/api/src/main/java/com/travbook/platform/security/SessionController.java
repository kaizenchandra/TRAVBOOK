package com.travbook.platform.security;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.UUID;
@RestController
public class SessionController {
    private final boolean enabled,local;
    private final String origin;
    public SessionController(@Value("${travbook.login-enabled}") boolean enabled,@Value("${travbook.local-identity}") boolean local,
                             @Value("${travbook.web-origin}") String origin) { this.enabled=enabled; this.local=local; this.origin=origin; }
    public record SessionView(boolean authenticated,boolean loginEnabled,String identityMode,String csrfToken,String csrfHeaderName) {}
    @GetMapping("/auth/session") public SessionView session(Authentication auth,CsrfToken csrf) {
        return new SessionView(auth!=null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken),enabled,enabled?(local?"LOCAL_OIDC":"OIDC"):"DISABLED",csrf.getToken(),csrf.getHeaderName());
    }
    @GetMapping("/auth/login") public void login(@RequestParam(required=false) UUID offerId,@RequestParam(required=false) UUID requestId,@RequestParam(required=false) UUID quoteId,@RequestParam(required=false) UUID bookingId,@RequestParam(required=false) UUID bookingQuoteId,@RequestParam(required=false) String stay,HttpServletRequest request,HttpServletResponse response) throws IOException {
        if(!enabled) { SecurityConfiguration.problem(request,response,503,"IDENTITY_UNAVAILABLE","Sign-in is not configured."); return; }
        if(java.util.stream.Stream.of(offerId,quoteId,bookingId,bookingQuoteId,stay).filter(java.util.Objects::nonNull).count()>1 || stay!=null&&!StayReturnPath.valid(stay)) { SecurityConfiguration.problem(request,response,400,"INVALID_REQUEST","Choose one valid return resource."); return; }
        if(stay!=null)request.getSession().setAttribute("stayReturn",stay);else request.getSession().removeAttribute("stayReturn");
        if(offerId!=null)request.getSession().setAttribute("quoteReturnOffer",offerId);
        else request.getSession().removeAttribute("quoteReturnOffer");
        if(requestId!=null)request.getSession().setAttribute("quoteReturnIntent",requestId);
        else request.getSession().removeAttribute("quoteReturnIntent");
        if(quoteId!=null)request.getSession().setAttribute("quoteReturnId",quoteId);
        else request.getSession().removeAttribute("quoteReturnId");
        if(bookingId!=null)request.getSession().setAttribute("bookingReturnId",bookingId);else request.getSession().removeAttribute("bookingReturnId");
        if(bookingQuoteId!=null)request.getSession().setAttribute("bookingReturnQuoteId",bookingQuoteId);else request.getSession().removeAttribute("bookingReturnQuoteId");
        // A relative servlet redirect can expose the internal reverse-proxy host to the browser.
        // WEB_ORIGIN is validated at startup; never derive this target from forwarded/client headers.
        response.sendRedirect(origin+"/oauth2/authorization/travbook");
    }
}

