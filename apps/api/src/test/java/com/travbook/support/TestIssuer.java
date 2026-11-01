package com.travbook.support;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
public final class TestIssuer implements AutoCloseable {
    private final RSAKey signing;private final HttpServer server;
    public TestIssuer(){try{
        signing=new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",exchange->{
            var value=exchange.getRequestURI().getPath().equals("/jwks")?new JWKSet(signing.toPublicJWK()).toString():JsonMapper.builder().build().writeValueAsString(Map.of("issuer",url(),"jwks_uri",url()+"/jwks"));
            byte[] body=value.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });server.start();
    }catch(Exception e){throw new IllegalStateException(e);}}
    public String url(){return "http://127.0.0.1:"+server.getAddress().getPort();}
    public String token(String subject,String scope)throws Exception {
        return token(subject,scope,Map.of());
    }
    public String token(String subject,String scope,Map<String,Object> extra)throws Exception {
        var builder=new JWTClaimsSet.Builder().issuer(url()).subject(subject).audience("travbook-api").issueTime(Date.from(Instant.now())).expirationTime(Date.from(Instant.now().plusSeconds(600))).claim("scope",scope);
        extra.forEach(builder::claim);var claims=builder.build();
        var token=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signing.getKeyID()).type(JOSEObjectType.JWT).build(),claims);token.sign(new RSASSASigner(signing));return token.serialize();
    }
    public void close(){server.stop(0);}
}
