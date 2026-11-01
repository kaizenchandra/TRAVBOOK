package com.travbook.platform.security;
import java.time.*;import java.util.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;
/** Privilege comes only from signed, verified identity claims; never client headers or form input. */
public final class OperatorIdentity {
 private OperatorIdentity(){}
 public static String require(Authentication auth,Instant now){Map<String,Object> claims;String issuer,subject;if(auth==null||!auth.isAuthenticated())throw new AccessDeniedException("Operator authentication required");if(auth.getPrincipal() instanceof Jwt jwt){claims=jwt.getClaims();issuer=jwt.getIssuer().toString();subject=jwt.getSubject();}else if(auth.getPrincipal() instanceof OidcUser user){claims=user.getIdToken().getClaims();issuer=user.getIssuer().toString();subject=user.getSubject();}else throw new AccessDeniedException("Verified operator required");
  var roles=claims.get("travbook_roles");var amr=claims.get("amr");Object time=claims.get("auth_time");Instant at=time instanceof Instant i?i:time instanceof Number n?Instant.ofEpochSecond(n.longValue()):null;
  if(!(roles instanceof Collection<?> r)||!r.contains("operations_reconcile")||!(amr instanceof Collection<?> factors)||!(factors.contains("mfa")||factors.contains("pwd")&&factors.contains("otp"))||at==null||at.isBefore(now.minusSeconds(600))||at.isAfter(now.plusSeconds(30)))throw new AccessDeniedException("Recent MFA and operations role required");
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((issuer+"\u0000"+subject).getBytes(StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
 }
 public static boolean allowed(Authentication a){try{require(a,Instant.now());return true;}catch(RuntimeException e){return false;}}
}
