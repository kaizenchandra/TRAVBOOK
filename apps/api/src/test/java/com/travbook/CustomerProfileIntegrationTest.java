package com.travbook;

import com.travbook.support.TestIssuer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class CustomerProfileIntegrationTest {
    static final TestIssuer issuer=new TestIssuer();
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);
        r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);
    }
    @AfterAll static void close(){issuer.close();}
    @Autowired JdbcClient jdbc;@Autowired ObjectMapper json;@LocalServerPort int port;
    final HttpClient http=HttpClient.newHttpClient();
    HttpResponse<String> request(String method,String token,String key,Object body)throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/me/profile")).timeout(Duration.ofSeconds(10));
        if(token!=null)builder.header("Authorization","Bearer "+token);
        if(key!=null)builder.header("Idempotency-Key",key);
        if(body!=null)builder.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));else builder.GET();
        return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode checked(HttpResponse<String> response,int status){assertThat(response.statusCode()).as(response.body()).isEqualTo(status);return json.readTree(response.body());}
    String token()throws Exception{return issuer.token(UUID.randomUUID().toString(),"");}
    JsonNode get(String token)throws Exception{return checked(request("GET",token,null,null),200);}
    Map<String,Object> input(JsonNode profile,String name){return new LinkedHashMap<>(Map.of("expectedCustomerId",profile.get("customerId").asString(),"expectedVersion",profile.get("version").asLong(),"displayName",name,"email","profile@example.test","phone","+919876543210","consent",true));}
    int count(String table,String owner){return jdbc.sql("select count(*) from "+table+" where customer_id=:owner").param("owner",UUID.fromString(owner)).query(Integer.class).single();}

    @Test void realDatabaseProfileIsOwnedAndCannotChangeIdentityOrClaimVerifiedContacts()throws Exception {
        assertThat(request("GET",null,null,null).statusCode()).isEqualTo(401);
        String alice=token(),bob=token();var original=get(alice);assertThat(original.get("version").asLong()).isZero();
        var saved=checked(request("PUT",alice,UUID.randomUUID().toString(),input(original,"Alex")),200).get("profile");
        assertThat(saved.get("version").asLong()).isEqualTo(1);assertThat(saved.get("displayName").asString()).isEqualTo("Alex");
        assertThat(saved.get("emailVerified").asBoolean()).isFalse();assertThat(saved.get("phoneVerified").asBoolean()).isFalse();
        assertThat(get(alice)).isEqualTo(saved);var other=get(bob);assertThat(other.get("displayName").asString()).isEmpty();
        assertThat(other.get("customerId").asString()).isNotEqualTo(saved.get("customerId").asString());
        var denied=checked(request("PUT",bob,UUID.randomUUID().toString(),input(saved,"Wrong account")),409);
        assertThat(denied.get("code").asString()).isEqualTo("PROFILE_IDENTITY_CONFLICT");
        assertThat(get(alice)).isEqualTo(saved);assertThat(get(bob)).isEqualTo(other);
        var injected=input(saved,"Injected");injected.put("customerId",other.get("customerId").asString());
        assertThat(request("PUT",alice,UUID.randomUUID().toString(),injected).statusCode()).isEqualTo(400);
    }
    @Test void canonicalRetriesDoNotCreateHistoryOrRetainOldContactValues()throws Exception {
        String auth=token(),key=UUID.randomUUID().toString();var original=get(auth);var body=input(original,"  A\u0301lex  ");
        var first=checked(request("PUT",auth,key,body),200);assertThat(first.get("profile").get("displayName").asString()).isEqualTo("Álex");
        body.put("displayName","Álex");var replay=checked(request("PUT",auth,key,body),200);
        assertThat(replay.get("replayed").asBoolean()).isTrue();assertThat(replay.get("appliedVersion").asLong()).isEqualTo(1);
        var cleared=input(first.get("profile"),"");cleared.put("email","");cleared.put("phone","");
        checked(request("PUT",auth,UUID.randomUUID().toString(),cleared),200);
        replay=checked(request("PUT",auth,key,body),200);assertThat(replay.get("appliedVersion").asLong()).isEqualTo(1);
        assertThat(replay.get("profile").get("version").asLong()).isEqualTo(2);assertThat(replay.get("profile").get("email").asString()).isEmpty();
        body.put("displayName","Changed");assertThat(checked(request("PUT",auth,key,body),409).get("code").asString()).isEqualTo("IDEMPOTENCY_CONFLICT");
        var owner=original.get("customerId").asString();assertThat(count("customer_profile_command",owner)).isEqualTo(2);assertThat(count("customer_profile_audit",owner)).isEqualTo(2);
        for(String table:List.of("customer_profile_audit","customer_profile_command")) {
            var history=jdbc.sql("select row_to_json(t)::text from "+table+" t where customer_id=:owner").param("owner",UUID.fromString(owner)).query(String.class).list();
            assertThat(history.toString()).doesNotContain("profile@example.test","9876543210","Álex");
            assertThatThrownBy(()->jdbc.sql("delete from "+table+" where customer_id=:owner").param("owner",UUID.fromString(owner)).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        }
    }
    @Test void concurrentCommandsSerializeAndRejectStaleEdits()throws Exception {
        String auth=token(),key=UUID.randomUUID().toString();var original=get(auth);var body=input(original,"Concurrent");
        try(var pool=Executors.newFixedThreadPool(4)) {
            var calls=new ArrayList<Callable<HttpResponse<String>>>();for(int i=0;i<4;i++)calls.add(()->request("PUT",auth,key,body));
            for(var f:pool.invokeAll(calls))assertThat(checked(f.get(),200).get("appliedVersion").asLong()).isEqualTo(1);
            var current=get(auth);var a=input(current,"First");var b=input(current,"Second");
            var results=pool.invokeAll(List.<Callable<HttpResponse<String>>>of(()->request("PUT",auth,UUID.randomUUID().toString(),a),()->request("PUT",auth,UUID.randomUUID().toString(),b)));
            assertThat(List.of(results.get(0).get().statusCode(),results.get(1).get().statusCode())).containsExactlyInAnyOrder(200,409);
        }
        assertThat(get(auth).get("version").asLong()).isEqualTo(2);assertThat(count("customer_profile_audit",original.get("customerId").asString())).isEqualTo(2);
    }
    @Test void failedAuditRollsBackProfileAndCommandAndRetryCanComplete()throws Exception {
        String auth=token(),key=UUID.randomUUID().toString();var original=get(auth);var body=input(original,"Atomic");
        String owner=UUID.fromString(original.get("customerId").asString()).toString();
        jdbc.sql("CREATE FUNCTION reject_profile_audit_fixture() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN IF NEW.customer_id='"+owner+"'::uuid THEN RAISE EXCEPTION 'audit unavailable'; END IF; RETURN NEW; END; $$").update();
        jdbc.sql("CREATE TRIGGER profile_audit_fixture BEFORE INSERT ON customer_profile_audit FOR EACH ROW EXECUTE FUNCTION reject_profile_audit_fixture()").update();
        try {var failed=request("PUT",auth,key,body);assertThat(failed.statusCode()).isEqualTo(503);assertThat(failed.body()).doesNotContain("profile@example.test","Atomic");
            assertThat(get(auth).get("version").asLong()).isZero();assertThat(count("customer_profile_command",owner)).isZero();
        }finally{jdbc.sql("DROP TRIGGER profile_audit_fixture ON customer_profile_audit").update();jdbc.sql("DROP FUNCTION reject_profile_audit_fixture()").update();}
        assertThat(checked(request("PUT",auth,key,body),200).get("appliedVersion").asLong()).isEqualTo(1);
    }
    @Test void invalidContactConsentVersionAndKeysAreRejectedWithoutWrites()throws Exception {
        String auth=token();var original=get(auth);
        for(var bad:List.of(Map.entry("email",(Object)"not-an-email"),Map.entry("phone",(Object)"123"),Map.entry("consent",(Object)false),Map.entry("displayName",(Object)"Line\nBreak"))) {
            var body=input(original,"Valid");body.put(bad.getKey(),bad.getValue());assertThat(request("PUT",auth,UUID.randomUUID().toString(),body).statusCode()).isEqualTo(422);
        }
        assertThat(request("PUT",auth,"short",input(original,"Valid")).statusCode()).isEqualTo(422);
        assertThat(request("PUT",auth,null,input(original,"Valid")).statusCode()).isEqualTo(400);
        assertThat(get(auth).get("version").asLong()).isZero();
    }
}
