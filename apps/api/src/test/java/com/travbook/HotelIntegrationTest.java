package com.travbook;
import com.travbook.hotels.application.port.out.HotelSearchRepository;
import com.travbook.hotels.adapter.out.persistence.JdbcHotelSearches;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
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
@Testcontainers
@org.springframework.context.annotation.Import(HotelIntegrationTest.TimeConfig.class)
class HotelIntegrationTest {
    @org.springframework.boot.test.context.TestConfiguration static class TimeConfig {
        @org.springframework.context.annotation.Bean @org.springframework.context.annotation.Primary MutableClock hotelClock(){return new MutableClock();}
    }
    static class MutableClock extends Clock {volatile Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}public Instant instant(){return now;}}
    @Autowired MutableClock clock;
    @BeforeEach void resetTime(){clock.now=Instant.now();}
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);}
    @LocalServerPort int port;@Autowired ObjectMapper json;@Autowired JdbcClient jdbc;@Autowired HotelSearchRepository repository;@Autowired PlatformTransactionManager manager;
    final HttpClient http=HttpClient.newHttpClient();
    HttpResponse<String> call(String path,Object body)throws Exception{var r=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));if(body==null)r.GET();else r.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));return http.send(r.build(),HttpResponse.BodyHandlers.ofString());}
    JsonNode tree(HttpResponse<String> r,int status){assertThat(r.statusCode()).as(r.body()).isEqualTo(status);assertThat(r.headers().firstValue("X-Correlation-ID")).isPresent();return json.readTree(r.body());}
    Map<String,Object> query(String city,List<?> rooms){var day=LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(7);return Map.of("destination",city,"checkIn",day.toString(),"checkOut",day.plusDays(2).toString(),"rooms",rooms);}
    List<?> adults(int count){return Collections.nCopies(count,Map.of("adults",2,"childAges",List.of()));}
    JsonNode search(String city,int count)throws Exception{return tree(call("/api/v1/hotels/search",query(city,adults(count))),200);}
    JsonNode rooms(JsonNode page,String property)throws Exception{return tree(call("/api/v1/hotels/"+property+"/rooms?searchId="+page.get("id").asString(),null),200);}
    List<String> selection(JsonNode rooms,String plan){var ids=new ArrayList<String>();for(var o:rooms.get("offers"))if(o.get("planCode").asString().equals(plan))ids.add(o.get("id").asString());return ids;}
    @Test void searchFiltersDetailsMultipleRoomsAndRepriceSurviveRepositoryRecreation()throws Exception{
        var page=search("Delhi",2);assertThat(page.get("total").asInt()).isEqualTo(4);assertThat(page.get("partial").asBoolean()).isFalse();String id=page.get("id").asString();
        var filtered=tree(call("/api/v1/hotel-searches/"+id+"?type=HOTEL&minStars=5&amenity=POOL&refundable=true&sort=RATING",null),200);assertThat(filtered.get("total").asInt()).isEqualTo(1);
        String property=filtered.get("items").get(0).get("property").get("id").asString();var detail=rooms(page,property);assertThat(detail.get("offers").size()).isEqualTo(8);assertThat(detail.get("property").get("images").size()).isEqualTo(2);
        var ids=selection(detail,"FAMILY");var checked=tree(call("/api/v1/hotels/offers/"+ids.getFirst()+"/reprice",Map.of("offerIds",ids)),200);assertThat(checked.get("changed").asBoolean()).isTrue();assertThat(checked.get("reservationCreated").asBoolean()).isFalse();assertThat(checked.get("offers").size()).isEqualTo(2);
        var fresh=new JdbcHotelSearches(jdbc,new TransactionTemplate(manager),json);assertThat(fresh.find(UUID.fromString(id))).isEqualTo(repository.find(UUID.fromString(id)));assertThat(fresh.findByOffer(UUID.fromString(ids.getFirst()))).isPresent();
        assertThat(jdbc.sql("select count(*) from hotel_offer_index where search_id=:id").param("id",UUID.fromString(id)).query(Integer.class).single()).isEqualTo(32);
        assertThat(jdbc.sql("select count(*) from booking_intent").query(Integer.class).single()).isZero();assertThat(jdbc.sql("select count(*) from payment_account").query(Integer.class).single()).isZero();
    }
    @Test void missingRoomsMixedPropertiesSearchesAndPriceInjectionAreRejected()throws Exception{
        var page=search("Delhi",2);var a=rooms(page,page.get("items").get(0).get("property").get("id").asString());var b=rooms(page,page.get("items").get(1).get("property").get("id").asString());var ids=selection(a,"FLEX");var other=selection(b,"FLEX");String path="/api/v1/hotels/offers/"+ids.getFirst()+"/reprice";
        tree(call(path,Map.of("offerIds",List.of(ids.getFirst()))),422);tree(call(path,Map.of("offerIds",List.of(ids.getFirst(),other.get(1)))),422);
        tree(call(path,Map.of("offerIds",List.of(ids.getFirst(),ids.getFirst()))),422);tree(call(path,Map.of("offerIds",List.of(ids.get(1),ids.getFirst()))),422);
        tree(call(path,Map.of("offerIds",ids,"total","0.01")),400);
        var newer=search("Delhi",2);var newRooms=rooms(newer,newer.get("items").get(0).get("property").get("id").asString());tree(call(path,Map.of("offerIds",List.of(ids.getFirst(),selection(newRooms,"FLEX").get(1)))),422);
        tree(call("/api/v1/hotels/missing/rooms?searchId="+page.get("id").asString(),null),404);
    }
    @Test void invalidDatesAgesAndOccupancyFailBeforeSupplierCall()throws Exception{
        tree(call("/api/v1/hotels/search",query("Delhi",List.of(Map.of("adults",0,"childAges",List.of())))),400);
        tree(call("/api/v1/hotels/search",query("Delhi",List.of(Map.of("adults",1,"childAges",List.of(18))))),400);
        var q=new HashMap<>(query("Delhi",adults(1)));q.put("checkOut",q.get("checkIn"));tree(call("/api/v1/hotels/search",q),422);
        tree(call("/api/v1/hotels/search",query("Delhi",adults(5))),400);
    }
    @Test void partialOutageEmptyAndTotalOutageRemainDistinctAndRoomExhaustionIsNotSuccess()throws Exception{
        assertThat(search("Goa",1).get("partial").asBoolean()).isTrue();assertThat(search("Jaipur",1).get("total").asInt()).isZero();tree(call("/api/v1/hotels/search",query("Shimla",adults(1))),503);
        var p=search("Delhi",1);var r=rooms(p,p.get("items").get(0).get("property").get("id").asString());var ids=selection(r,"STUDIO");assertThat(tree(call("/api/v1/hotels/offers/"+ids.getFirst()+"/reprice",Map.of("offerIds",ids)),409).get("code").asString()).isEqualTo("OFFER_UNAVAILABLE");
    }
    @Test void failedSnapshotTransactionLeavesNoPartialLocalState()throws Exception{
        var p=search("Mumbai",1);var s=repository.find(UUID.fromString(p.get("id").asString())).orElseThrow();var duplicate=new com.travbook.hotels.domain.model.HotelSnapshot(UUID.randomUUID(),s.criteria(),s.properties(),s.offers(),s.coverage(),s.createdAt(),s.expiresAt());
        assertThatThrownBy(()->repository.save(duplicate)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);assertThat(repository.find(duplicate.id())).isEmpty();
    }
    @Test void expiredSnapshotsRejectResultsRoomsAndRepricingWithoutDeletingHistory()throws Exception{
        var p=search("Delhi",1);String id=p.get("id").asString(),property=p.get("items").get(0).get("property").get("id").asString();var r=rooms(p,property);var ids=selection(r,"FLEX");
        clock.now=Instant.parse(p.get("expiresAt").asString());
        assertThat(tree(call("/api/v1/hotel-searches/"+id,null),409).get("code").asString()).isEqualTo("SEARCH_EXPIRED");
        tree(call("/api/v1/hotels/"+property+"/rooms?searchId="+id,null),409);tree(call("/api/v1/hotels/offers/"+ids.getFirst()+"/reprice",Map.of("offerIds",ids)),409);
        assertThat(repository.find(UUID.fromString(id))).isPresent();
    }
}
