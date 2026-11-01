package com.travbook;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="travbook.booking.worker-enabled=false")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(QuotationIntegrationTest.TimeConfiguration.class)
@Testcontainers
class QuotationIntegrationTest {
    static final RSAKey SIGNING=key();
    static final HttpServer ISSUER=issuerServer();
    static String issuer(){return "http://127.0.0.1:"+ISSUER.getAddress().getPort();}
    static RSAKey key(){try{return new RSAKeyGenerator(2048).keyID(UUID.randomUUID().toString()).generate();}catch(Exception e){throw new IllegalStateException(e);}}
    static HttpServer issuerServer() {
        try {
            var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/",exchange->{
                String root="http://127.0.0.1:"+server.getAddress().getPort();
                String body=exchange.getRequestURI().getPath().equals("/jwks")?new JWKSet(SIGNING.toPublicJWK()).toString():JsonMapper.builder().build().writeValueAsString(Map.of("issuer",root,"jwks_uri",root+"/jwks"));
                byte[] bytes=body.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
            });server.start();return server;
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
        r.add("travbook.oidc-issuer",QuotationIntegrationTest::issuer);r.add("travbook.local-identity",()->true);
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary MutableClock testClock(){return new MutableClock();}
    }
    static class MutableClock extends Clock {
        volatile Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}public Instant instant(){return now;}
    }
    @Autowired MutableClock clock;@Autowired ObjectMapper json;@Autowired JdbcClient jdbc;@LocalServerPort int port;
    @Autowired org.springframework.session.SessionRepository<?> sessions;
    final HttpClient http=HttpClient.newHttpClient();
    @BeforeEach void resetClock(){clock.now=Instant.now();}
    @AfterAll static void closeIssuer(){ISSUER.stop(0);}
    String token(String subject,String scopes) throws Exception {return token(subject,scopes,issuer(),"travbook-api",Instant.now().plusSeconds(600),SIGNING);}
    String token(String subject,String scopes,String issuer,String audience,Instant expiry,RSAKey key) throws Exception {
        var claims=new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(audience).issueTime(Date.from(Instant.now().minusSeconds(1))).claim("scope",scopes);
        if(expiry!=null)claims.expirationTime(Date.from(expiry));
        var jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).type(JOSEObjectType.JWT).build(),claims.build());jwt.sign(new RSASSASigner(key));return jwt.serialize();
    }
    HttpResponse<String> call(String method,String path,String token,String key,String body)throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));
        if(token!=null)request.header("Authorization","Bearer "+token);if(key!=null)request.header("Idempotency-Key",key);
        if(method.equals("POST"))request.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body));else request.GET();
        return http.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode tree(HttpResponse<String> response,int status){assertThat(response.statusCode()).as(response.body()).isEqualTo(status);return json.readTree(response.body());}
    String offer(int index)throws Exception {
        String body=json.writeValueAsString(Map.of("origin","DEL","destination","BOM","departureDate",LocalDate.now().plusDays(2).toString(),"adults",1,"children",0,"infants",0,"cabin","ECONOMY"));
        body=body.substring(0,body.length()-1)+",\"returnDate\":null}";
        return tree(call("POST","/api/v1/flights/search",null,null,body),200).get("offers").get(index).get("id").asString();
    }
    String createBody(String offer){return "{\"offerId\":\""+offer+"\"}";}
    String newKey(){return UUID.randomUUID().toString();}
    int count(String sql,String id){return jdbc.sql(sql).param("id",UUID.fromString(id)).query(Integer.class).single();}
    @Test void signedTokensRequireIssuerAudienceExpirySignatureAndScopes()throws Exception {
        assertThat(call("GET","/api/v1/me",null,null,"").statusCode()).isEqualTo(401);
        for(String bad:List.of(token("alice","quotes:read","https://wrong.invalid","travbook-api",Instant.now().plusSeconds(600),SIGNING),
            token("alice","quotes:read",issuer(),"wrong",Instant.now().plusSeconds(600),SIGNING),
            token("alice","quotes:read",issuer(),"travbook-api",Instant.now().minusSeconds(120),SIGNING),
            token("alice","quotes:read",issuer(),"travbook-api",null,SIGNING),
            token("alice","quotes:read",issuer(),"travbook-api",Instant.now().plusSeconds(600),key()))) {
            var result=tree(call("GET","/api/v1/me",bad,null,""),401);assertThat(result.get("code").asString()).isEqualTo("UNAUTHENTICATED");
        }
        assertThat(call("POST","/api/v1/quotes",token("alice","quotes:read"),newKey(),createBody(offer(0))).statusCode()).isEqualTo(403);
    }
    @Test void customerIdentityIsStableAndIsolated()throws Exception {
        String alice=token("customer-alice","quotes:read quotes:write"),bob=token("customer-bob","quotes:read quotes:write");
        var first=tree(call("GET","/api/v1/me",alice,null,""),200);
        assertThat(tree(call("GET","/api/v1/me",alice,null,""),200)).isEqualTo(first);
        assertThat(tree(call("GET","/api/v1/me",bob,null,""),200).get("id")).isNotEqualTo(first.get("id"));
        assertThat(first.toString()).doesNotContain("customer-alice","access_token","issuer");
    }
    @Test void concurrentQuoteCreationPersistsOneSnapshotCommandAndEvent()throws Exception {
        String auth=token("concurrent-owner","quotes:read quotes:write"),offer=offer(2),key=newKey();
        try(var pool=Executors.newFixedThreadPool(6)) {
            var tasks=new ArrayList<Callable<JsonNode>>();for(int i=0;i<6;i++)tasks.add(()->tree(call("POST","/api/v1/quotes",auth,key,createBody(offer)),201));
            var results=pool.invokeAll(tasks);var original=results.getFirst().get();
            for(var result:results)assertThat(result.get()).isEqualTo(original);
            String id=original.get("id").asString();
            assertThat(original.get("priceChanged").asBoolean()).isTrue();assertThat(original.get("total").get("amount").asString()).isEqualTo("4681.00");
            assertThat(count("select count(*) from pricing_quotation where id=:id",id)).isEqualTo(1);
            assertThat(count("select count(*) from pricing_command where resource_id=:id",id)).isEqualTo(1);
            assertThat(count("select count(*) from pricing_outbox where aggregate_id=:id",id)).isEqualTo(1);
            assertThat(tree(call("POST","/api/v1/quotes",auth,key,createBody(offer(0))),409).get("code").asString()).isEqualTo("IDEMPOTENCY_CONFLICT");
        }
    }
    @Test void ownerAndExactVersionProtectAcceptanceAndDifferentKeysStillCreateOneReceipt()throws Exception {
        String alice=token("accept-alice","quotes:read quotes:write"),bob=token("accept-bob","quotes:read quotes:write");
        var q=tree(call("POST","/api/v1/quotes",alice,newKey(),createBody(offer(0))),201);String id=q.get("id").asString();
        assertThat(call("GET","/api/v1/quotes/"+id,bob,null,"").statusCode()).isEqualTo(404);
        assertThat(call("POST","/api/v1/quotes/"+id+"/acceptance",bob,newKey(),"{\"version\":1}").statusCode()).isEqualTo(404);
        assertThat(tree(call("POST","/api/v1/quotes/"+id+"/acceptance",alice,newKey(),"{\"version\":2}"),409).get("code").asString()).isEqualTo("QUOTE_VERSION_CONFLICT");
        String key=newKey();
        try(var pool=Executors.newFixedThreadPool(6)) {
            var tasks=new ArrayList<Callable<JsonNode>>();for(int i=0;i<6;i++){String k=i<3?key:newKey();tasks.add(()->tree(call("POST","/api/v1/quotes/"+id+"/acceptance",alice,k,"{\"version\":1}"),200));}
            var results=pool.invokeAll(tasks);var receipt=results.getFirst().get();for(var result:results)assertThat(result.get()).isEqualTo(receipt);
            assertThat(count("select count(*) from pricing_acceptance where quote_id=:id",id)).isEqualTo(1);
            assertThat(count("select count(*) from pricing_outbox where aggregate_id=:id",receipt.get("id").asString())).isEqualTo(1);
        }
        var detail=tree(call("GET","/api/v1/quotes/"+id,alice,null,""),200);assertThat(detail.get("quote")).isEqualTo(q);
        assertThat(detail.get("acceptance").isNull()).isFalse();
        assertThat(call("POST","/api/v1/quotes/"+id+"/acceptance",alice,key,"{\"version\":2}").statusCode()).isEqualTo(409);
    }
    @Test void expiryPreventsNewAcceptanceButReplaysRemainStable()throws Exception {
        String auth=token("expiry-owner","quotes:read quotes:write"),createKey=newKey(),acceptKey=newKey(),offer=offer(0);
        var q=tree(call("POST","/api/v1/quotes",auth,createKey,createBody(offer)),201);String id=q.get("id").asString();
        var accepted=tree(call("POST","/api/v1/quotes/"+id+"/acceptance",auth,acceptKey,"{\"version\":1}"),200);
        var other=tree(call("POST","/api/v1/quotes",auth,newKey(),createBody(offer)),201);
        clock.now=clock.now.plusSeconds(180);
        assertThat(tree(call("GET","/api/v1/quotes/"+id,auth,null,""),200).get("expired").asBoolean()).isTrue();
        assertThat(tree(call("POST","/api/v1/quotes",auth,createKey,createBody(offer)),201)).isEqualTo(q);
        assertThat(tree(call("POST","/api/v1/quotes/"+id+"/acceptance",auth,acceptKey,"{\"version\":1}"),200)).isEqualTo(accepted);
        assertThat(tree(call("POST","/api/v1/quotes/"+other.get("id").asString()+"/acceptance",auth,newKey(),"{\"version\":1}"),409).get("code").asString()).isEqualTo("QUOTE_EXPIRED");
    }
    @Test void immutableHistoryAndRollbackAreEnforcedInPostgres()throws Exception {
        String auth=token("history-owner","quotes:read quotes:write");
        var q=tree(call("POST","/api/v1/quotes",auth,newKey(),createBody(offer(0))),201);String id=q.get("id").asString();
        assertThatThrownBy(()->jdbc.sql("update pricing_quotation set total=1 where id=:id").param("id",UUID.fromString(id)).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.sql("delete from pricing_quotation where id=:id").param("id",UUID.fromString(id)).update()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        String key=newKey();assertThat(call("POST","/api/v1/quotes/"+id+"/acceptance",auth,key,"{\"version\":2}").statusCode()).isEqualTo(409);
        assertThat(jdbc.sql("select count(*) from pricing_command where command_key=:key").param("key",key).query(Integer.class).single()).isZero();
        assertThat(count("select count(*) from pricing_acceptance where quote_id=:id",id)).isZero();
        var after=tree(call("GET","/api/v1/quotes/"+id,auth,null,""),200);assertThat(after.get("quote")).isEqualTo(q);
    }
    @Test void callerCannotSupplyOwnerOrPriceAndMissingKeysAreRejected()throws Exception {
        String auth=token("input-owner","quotes:read quotes:write");String body=createBody(offer(0));
        assertThat(call("POST","/api/v1/quotes",auth,null,body).statusCode()).isEqualTo(400);
        assertThat(call("POST","/api/v1/quotes",auth,"short",body).statusCode()).isEqualTo(422);
        assertThat(call("POST","/api/v1/quotes",auth,newKey(),body.replace("}",",\"total\":1}")).statusCode()).isEqualTo(400);
        assertThat(call("POST","/api/v1/quotes",auth,newKey(),body.replace("}",",\"customerId\":\""+UUID.randomUUID()+"\"}")).statusCode()).isEqualTo(400);
        assertThat(call("POST","/api/v1/quotes",null,newKey(),body).statusCode()).isIn(401,403);
        var lowercase=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/quotes"))
            .header("Authorization","bearer "+auth).header("Content-Type","application/json").header("Idempotency-Key",newKey())
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        assertThat(http.send(lowercase,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(201);
    }
    @Test void jdbcSessionRestoresIdentityButCannotOutliveItsOidcToken()throws Exception {
        for(boolean expired:List.of(false,true)) {
            var token=new org.springframework.security.oauth2.core.oidc.OidcIdToken("test-only-id-token",Instant.now().minusSeconds(600),
                Instant.now().plusSeconds(expired?-60:300),Map.of("iss",issuer(),"sub","session-owner","aud",List.of("travbook-web")));
            var authorities=List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_quotes:read"));
            var user=new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(authorities,token);
            var auth=new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(user,authorities,"travbook");
            String sessionId=saveSession(sessions,auth);
            var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/me"))
                .header("Cookie","TRAVBOOK_SESSION="+Base64.getEncoder().encodeToString(sessionId.getBytes(StandardCharsets.UTF_8))).GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofString());assertThat(response.statusCode()).as(response.body()).isEqualTo(expired?401:200);
            if(expired)assertThat(sessions.findById(sessionId)).isNull();else sessions.deleteById(sessionId);
        }
    }
    private <S extends org.springframework.session.Session> String saveSession(org.springframework.session.SessionRepository<S> repository,org.springframework.security.core.Authentication auth) {
        S session=repository.createSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT",new org.springframework.security.core.context.SecurityContextImpl(auth));
        repository.save(session);return session.getId();
    }
}
