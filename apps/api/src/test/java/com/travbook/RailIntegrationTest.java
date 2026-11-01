package com.travbook;
import com.travbook.rail.domain.model.Rail.*;
import com.travbook.rail.application.port.out.RailRepository;
import com.travbook.rail.adapter.out.persistence.JdbcRailSearches;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import tools.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(RailIntegrationTest.TimeConfig.class)
@Testcontainers
class RailIntegrationTest {
    @TestConfiguration static class TimeConfig {@Bean @Primary MutableClock testClock(){return new MutableClock();}}
    static class MutableClock extends Clock {volatile Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}public Instant instant(){return now;}}
    @Autowired MutableClock clock;@BeforeEach void reset(){clock.now=Instant.now();}
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @LocalServerPort int port;@Autowired ObjectMapper json;@Autowired JdbcClient jdbc;@Autowired RailRepository repository;@Autowired PlatformTransactionManager manager;
    final HttpClient client=HttpClient.newHttpClient();
    HttpResponse<String> call(String path,Object body)throws Exception{var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));if(body==null)r.GET();else r.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));return client.send(r.build(),HttpResponse.BodyHandlers.ofString());}
    JsonNode tree(HttpResponse<String> r,int status){assertThat(r.statusCode()).as(r.body()).isEqualTo(status);assertThat(r.headers().firstValue("X-Correlation-ID")).isPresent();return json.readTree(r.body());}
    Map<String,Object> query(String destination){return Map.of("origin","NDLS","destination",destination,"departureDate",LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2).toString(),"quota","GENERAL");}
    JsonNode search(String destination)throws Exception{return tree(call("/api/v1/trains/search",query(destination)),200);}
    @Test void stationsSearchFilterDetailsAndFreshAvailabilityUsePersistentCanonicalReferences()throws Exception{
        var stations=tree(call("/api/v1/railway-stations?q=delhi",null),200);assertThat(stations.size()).isEqualTo(1);assertThat(stations.get(0).get("code").asString()).isEqualTo("NDLS");
        var s=search("MMCT");String id=s.get("id").asString(),journey=s.get("items").get(0).get("id").asString();assertThat(s.get("total").asInt()).isGreaterThanOrEqualTo(2);
        var none=tree(call("/api/v1/train-searches/"+id+"?travelClass=THREE_AC&status=WAITLIST",null),200);assertThat(none.get("total").asInt()).isZero();
        var details=tree(call("/api/v1/trains/"+journey,null),200);assertThat(details.get("ticketingEnabled").asBoolean()).isFalse();
        var checked=tree(call("/api/v1/trains/"+journey+"/availability?travelClass=SL&quota=GENERAL",null),200);assertThat(checked.get("current").get("status").asString()).isEqualTo("RAC");assertThat(checked.get("changed").asBoolean()).isTrue();assertThat(checked.get("ticketingEnabled").asBoolean()).isFalse();
        var fresh=new JdbcRailSearches(jdbc,new TransactionTemplate(manager),json);assertThat(fresh.find(UUID.fromString(id))).isEqualTo(repository.find(UUID.fromString(id)));assertThat(fresh.forJourney(UUID.fromString(journey))).isPresent();
        assertThat(jdbc.sql("select count(*) from booking_intent").query(Integer.class).single()).isZero();assertThat(jdbc.sql("select count(*) from payment_account").query(Integer.class).single()).isZero();
    }
    @Test void unknownStationsInvalidRoutesClassesAndInjectedPricesAreRejected()throws Exception{
        var q=new HashMap<>(query("NDLS"));tree(call("/api/v1/trains/search",q),422);q.put("destination","ZZZ");tree(call("/api/v1/trains/search",q),422);q.put("destination","MMCT");q.put("total","1.00");tree(call("/api/v1/trains/search",q),400);
        var s=search("MMCT");String j=s.get("items").get(0).get("id").asString();tree(call("/api/v1/trains/"+j+"/availability?travelClass=FIRST&quota=GENERAL",null),400);tree(call("/api/v1/trains/"+j+"/availability",null),400);tree(call("/api/v1/trains/"+UUID.randomUUID(),null),404);
        assertThat(call("/api/v1/trains/pnr/1234567890",null).statusCode()).isIn(401,403);assertThat(call("/api/v1/trains/"+j+"/bookings",Map.of()).statusCode()).isIn(401,403);
    }
    @Test void partialOutageEmptySearchAndTotalOutageAreDifferent()throws Exception{assertThat(search("MAO").get("partial").asBoolean()).isTrue();assertThat(search("JP").get("total").asInt()).isZero();tree(call("/api/v1/trains/search",query("HWH")),503);}
    @Test void snapshotExpiryRejectsEveryDependentReadWithoutDeletingTheObservation()throws Exception{
        var s=search("MMCT");String id=s.get("id").asString(),j=s.get("items").get(0).get("id").asString();clock.now=Instant.parse(s.get("expiresAt").asString());
        tree(call("/api/v1/train-searches/"+id,null),409);tree(call("/api/v1/trains/"+j,null),409);tree(call("/api/v1/trains/"+j+"/availability?travelClass=SL&quota=GENERAL",null),409);assertThat(repository.find(UUID.fromString(id))).isPresent();
    }
    @Test void atomicSnapshotRollbackAndReadOnlyChecksDoNotOverwriteHistory()throws Exception{
        var page=search("MMCT");UUID id=UUID.fromString(page.get("id").asString());var s=repository.find(id).orElseThrow();var duplicate=new Snapshot(UUID.randomUUID(),s.criteria(),s.journeys(),s.coverage(),s.createdAt(),s.expiresAt());assertThatThrownBy(()->repository.save(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(repository.find(duplicate.id())).isEmpty();
        String path="/api/v1/trains/"+s.journeys().getFirst().id()+"/availability?travelClass=SL&quota=GENERAL";tree(call(path,null),200);tree(call(path,null),200);assertThat(repository.find(id)).contains(s);
    }
}
