package com.travbook;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties="travbook.booking.worker-enabled=false")
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class ApiIntegrationTest {
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",postgres::getJdbcUrl);
        registry.add("spring.datasource.username",postgres::getUsername);
        registry.add("spring.datasource.password",postgres::getPassword);
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired JdbcClient jdbc;
    @Autowired com.travbook.search.application.port.out.SearchRepository snapshots;
    final HttpClient client=HttpClient.newHttpClient();
    HttpResponse<String> get(String path)throws Exception {return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    HttpResponse<String> post(String path,String body)throws Exception {return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    String body(String destination) {return "{\"origin\":\"DEL\",\"destination\":\""+destination+"\",\"departureDate\":\""+LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2)+"\",\"returnDate\":null,\"adults\":1,\"children\":0,\"infants\":0,\"cabin\":\"ECONOMY\"}";}
    @Test void catalogSearchSnapshotDetailAndRepricePersistAcrossRequests()throws Exception {
        var locations=get("/api/v1/locations?q=mumbai");
        assertThat(locations.statusCode()).isEqualTo(200);
        assertThat(json.readTree(locations.body()).get(0).get("id").asString()).isEqualTo("BOM");
        var response=post("/api/v1/flights/search",body("BOM"));
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-Correlation-ID")).isPresent();
        assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
        var page=json.readTree(response.body());
        String searchId=page.get("id").asString();
        assertThat(page.get("total").asInt()).isEqualTo(8);
        assertThat(page.get("mode").asString()).isEqualTo("MOCK");
        assertThat(page.get("partial").asBoolean()).isFalse();
        assertThat(jdbc.sql("select count(*) from search_offer where search_id=:id").param("id",UUID.fromString(searchId)).query(Integer.class).single()).isEqualTo(8);
        var filtered=json.readTree(get("/api/v1/flights/searches/"+searchId+"?nonstop=true&size=20").body());
        assertThat(filtered.get("total").asInt()).isEqualTo(4);
        var original=page.get("offers").get(0);
        String offerId=original.get("id").asString();
        var detail=get("/api/v1/flights/offers/"+offerId);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat(json.readTree(detail.body()).get("total").get("amount").asString()).isEqualTo("3990.00");
        var reprice=post("/api/v1/flights/offers/"+offerId+"/reprice","");
        assertThat(reprice.statusCode()).isEqualTo(200);
        assertThat(json.readTree(reprice.body()).get("changed").asBoolean()).isFalse();
        assertThat(get("/api/v1/flights/offers/"+offerId).body()).isEqualTo(detail.body());
        assertThat(post("/api/v1/flights/search",body("DEL")).statusCode()).isEqualTo(422);
    }
    @Test void changedAndSoldOutAndOriginalSnapshotAreDistinct()throws Exception {
        var page=json.readTree(post("/api/v1/flights/search",body("BOM")).body());
        var all=json.readTree(get("/api/v1/flights/searches/"+page.get("id").asString()+"?size=20").body());
        for(var offer:all.get("offers")) {
            String id=offer.get("id").asString();
            if(offer.get("supplierReference").asString().endsWith("-2")) {
                var result=json.readTree(post("/api/v1/flights/offers/"+id+"/reprice","").body());
                assertThat(result.get("changed").asBoolean()).isTrue();
                assertThat(result.get("previousTotal")).isEqualTo(offer.get("total"));
                assertThat(json.readTree(get("/api/v1/flights/offers/"+id).body()).get("total")).isEqualTo(offer.get("total"));
            }
            if(offer.get("supplierReference").asString().endsWith("-4")) {
                var result=post("/api/v1/flights/offers/"+id+"/reprice","");
                assertThat(result.statusCode()).isEqualTo(409);
                assertThat(json.readTree(result.body()).get("code").asString()).isEqualTo("OFFER_UNAVAILABLE");
            }
        }
    }
    @Test void partialOutageAndSuccessfulEmptyAreDifferent()throws Exception {
        var partial=json.readTree(post("/api/v1/flights/search",body("GOI")).body());
        assertThat(partial.get("partial").asBoolean()).isTrue();
        assertThat(partial.get("total").asInt()).isEqualTo(4);
        var empty=json.readTree(post("/api/v1/flights/search",body("JAI")).body());
        assertThat(empty.get("partial").asBoolean()).isFalse();
        assertThat(empty.get("total").asInt()).isZero();
    }
    @Test void rejectsInvalidBodiesAndNonpublicRoutesWithoutLeakingInternals()throws Exception {
        assertThat(get("/api/v1/me/bookings").statusCode()).isEqualTo(401);
        assertThat(get("/actuator/prometheus").statusCode()).isEqualTo(401);
        assertThat(get("/api/v1/locations?q=%27%20OR%201=1").body()).isEqualTo("[]");
        var invalid=post("/api/v1/flights/search",body("BOM").replace("\"adults\":1","\"adults\":null"));
        assertThat(invalid.statusCode()).isEqualTo(400);
        assertThat(invalid.headers().firstValue("content-type").orElse("")).startsWith("application/problem+json");
        assertThat(invalid.body()).doesNotContain("Exception","java.","password");
        assertThat(post("/api/v1/flights/search",body("BOM").replace("\"adults\":1,","")).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/flights/search",body("BOM").replace("\"adults\":1","\"adults\":1,\"total\":1")).statusCode()).isEqualTo(400);
        assertThat(post("/api/v1/flights/search"," ".repeat(17000)).statusCode()).isEqualTo(413);
        assertThat(get("/api/v1/flights/offers/not-a-uuid").statusCode()).isEqualTo(400);
        assertThat(get("/api/v1/flights/offers/"+UUID.randomUUID()).statusCode()).isEqualTo(404);
    }
    @Test void databaseEnforcesMoneyAndForeignKeyConstraints() {
        assertThatThrownBy(()->jdbc.sql("insert into search_offer(id,search_id,total,currency,payload) values(:id,:search,-1,'INR','{}')").param("id",UUID.randomUUID()).param("search",UUID.randomUUID()).update())
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void snapshotAndOffersRollBackAtomicallyOnConstraintFailure() {
        var now=Instant.now();
        var query=new com.travbook.search.domain.model.FlightQuery("DEL","BOM",LocalDate.now().plusDays(2),null,1,0,0,com.travbook.search.domain.model.FlightQuery.Cabin.ECONOMY);
        var offer=new com.travbook.search.adapter.out.supplier.MockFlightSupplier("sample","Sample",0).search(query,now).getFirst();
        var id=UUID.randomUUID();
        var invalid=new com.travbook.search.domain.model.SearchSnapshot(id,query,List.of(offer,offer),List.of(),now,now.plusSeconds(900));
        assertThatThrownBy(()->snapshots.save(invalid)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.sql("select count(*) from search_snapshot where id=:id").param("id",id).query(Integer.class).single()).isZero();
        assertThat(jdbc.sql("select count(*) from search_offer where search_id=:id").param("id",id).query(Integer.class).single()).isZero();
    }
}

