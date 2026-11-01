package com.travbook;

import com.travbook.flightprovider.application.port.in.FlightProvider;
import com.travbook.flightprovider.application.port.out.ProviderStore;
import com.travbook.flightprovider.domain.model.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.shared.domain.*;
import com.travbook.support.TestIssuer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static com.travbook.flightprovider.domain.model.ProviderReservation.Action.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"travbook.demo.enabled=true","travbook.demo.worker-enabled=false","JOURNEY_WORKER_ENABLED=false","JOURNEY_CARE_WORKER_ENABLED=false","STAY_WORKER_ENABLED=false","RAIL_TICKET_WORKER_ENABLED=false","travbook.booking.worker-enabled=false","travbook.booking.events-enabled=false","travbook.booking.checkout-enabled=false","travbook.booking.cancellation-enabled=false","travbook.payments.refund-worker-enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(FlightProviderIntegrationTest.TimeConfig.class)
@Testcontainers
class FlightProviderIntegrationTest {
    static final TestIssuer issuer=new TestIssuer();
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);}
    @TestConfiguration static class TimeConfig{@Bean @Primary MutableClock providerClock(){return new MutableClock();}}
    static class MutableClock extends Clock {Instant now=Instant.parse("2030-01-01T00:00:00Z");public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return Clock.fixed(now,z);}public Instant instant(){return now;}}
    @Autowired FlightProvider provider;@Autowired ProviderStore store;@Autowired Customers customers;@Autowired MutableClock clock;@Autowired JdbcClient jdbc;@Autowired ObjectMapper json;
    @Autowired org.springframework.session.SessionRepository<?> sessions;
    @LocalServerPort int port;@TempDir Path temp;
    CustomerIdentity identity;String token;String flight;UUID owner;static int sequence;
    static final Path contractEvidence=Path.of(System.getProperty("basedir","."),"target","flight-provider-http.jsonl");
    @BeforeAll static void clearEvidence()throws Exception{Files.createDirectories(contractEvidence.getParent());Files.deleteIfExists(contractEvidence);}
    @BeforeEach void init()throws Exception{clock.now=Instant.parse("2030-01-01T00:00:00Z").plusSeconds(++sequence*30*86400L);identity=new CustomerIdentity(issuer.url(),"fp-"+UUID.randomUUID());owner=customers.resolve(identity).id();token=issuer.token(identity.subject(),"bookings:read bookings:write");flight="MOCK-FA-"+clock.now.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().plusDays(1);}
    @AfterAll static void close(){issuer.close();}
    @AfterEach void eventEvidence()throws Exception{var path=contractEvidence.resolveSibling("flight-provider-events.jsonl");Files.writeString(path,String.join("\n",jdbc.sql("select envelope::text from fp_event order by aggregate_id,version").query(String.class).list())+"\n");}
    String key(){return UUID.randomUUID().toString();}
    FlightFixture.Selection selection(String seat){return new FlightFixture.Selection("P1",30,seat,FlightFixture.Meal.VEG,FlightFixture.Baggage.KG20);}
    ProviderQuote quote(String seat){return provider.quote(identity,flight,List.of(selection(seat)),key(),UUID.randomUUID());}
    ProviderReservation reserve(ProviderQuote q){return provider.reserve(identity,q.id(),1,q.total(),true,key(),UUID.randomUUID());}
    ProviderReservation act(ProviderReservation r,ProviderReservation.Action action){return provider.act(identity,r.id(),action,List.of(),true,key(),UUID.randomUUID());}
    long effects(UUID id){return jdbc.sql("select count(*) from fp_money_effect where reservation_id=:id").param("id",id).query(Long.class).single();}
    HttpResponse<String> call(String method,String path,String auth,String key,Object body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/demo/flight-provider"+path)).timeout(Duration.ofSeconds(10));if(auth!=null)b.header("Authorization","Bearer "+auth);if(key!=null)b.header("Idempotency-Key",key);
        if(body==null)b.method(method,HttpRequest.BodyPublishers.noBody());else b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var result=HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
        Files.writeString(contractEvidence,json.writeValueAsString(Map.of("method",method.toLowerCase(Locale.ROOT),"path","/api/v1/demo/flight-provider"+path,"status",result.statusCode(),"body",method.equals("HEAD")?Map.of():json.readTree(result.body())))+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        return result;
    }
    JsonNode checked(HttpResponse<String> response,int status){assertThat(response.statusCode()).as(response.body()).isEqualTo(status);var value=json.readTree(response.body());if(status<300){assertThat(value.get("mode").asString()).isEqualTo("MOCK");assertThat(value.get("notice").asString()).contains("NOT VALID FOR TRAVEL");return value.get("data");}return value;}
    @Test void httpWorkflowIncludesAuthoritativeExtrasPaymentTicketAndPartialPassengerCheckIn()throws Exception {
        var capabilities=checked(call("GET","/capabilities",token,null,null),200);assertThat(capabilities.get("credentialStoreReference").isNull()).isTrue();
        assertThat(checked(call("GET","/flights?date="+flight.substring(8),token,null,null),200).size()).isEqualTo(1);
        var seats=checked(call("GET","/flights/"+flight+"/seats",token,null,null),200);assertThat(seats.get("seats").size()).isEqualTo(18);
        assertThat(checked(call("GET","/flights/"+flight+"/ancillaries",token,null,null),200).get("items").size()).isEqualTo(4);
        var selections=List.of(selection("2A"),new FlightFixture.Selection("P2",12,"2B",FlightFixture.Meal.NONE,FlightFixture.Baggage.NONE));
        var q=checked(call("POST","/quotes",token,key(),Map.of("flightId",flight,"selections",selections)),201);
        assertThat(q.get("total").get("amount").asString()).isEqualTo("10050.00");
        var r=checked(call("POST","/reservations",token,key(),Map.of("quoteId",q.get("id").asString(),"quoteVersion",1,"acceptedTotal",q.get("total"),"consent",true)),201);String id=r.get("id").asString();
        assertThat(call("POST","/reservations/"+id+"/tickets",token,key(),Map.of("outcome","ISSUE","consent",true)).statusCode()).isEqualTo(409);
        var payment=checked(call("POST","/reservations/"+id+"/payments",token,key(),Map.of("outcome","SUCCEED","consent",true)),200);
        assertThat(payment.get("payment").asString()).isEqualTo("CAPTURED");assertThat(payment.get("ticket").asString()).isEqualTo("NOT_ISSUED");
        assertThat(call("GET","/reservations/"+id+"/documents",token,null,null).statusCode()).isEqualTo(409);
        checked(call("POST","/reservations/"+id+"/tickets",token,key(),Map.of("outcome","ISSUE","consent",true)),200);
        var checkBody=Map.of("passengerIds",List.of("P1"),"consent",true);
        assertThat(call("POST","/reservations/"+id+"/check-in",token,key(),checkBody).statusCode()).isEqualTo(409);
        clock.now=FlightFixture.flight(flight).checkInOpens();String checkKey=key();
        var checkedIn=checked(call("POST","/reservations/"+id+"/check-in",token,checkKey,checkBody),200);assertThat(checkedIn.get("checkedIn").size()).isEqualTo(1);
        assertThat(checked(call("POST","/reservations/"+id+"/check-in",token,checkKey,checkBody),200)).isEqualTo(checkedIn);
        var documents=checked(call("GET","/reservations/"+id+"/documents",token,null,null),200);assertThat(documents.size()).isEqualTo(3);
        for(var doc:documents){assertThat(doc.get("validForTravel").asBoolean()).isFalse();assertThat(doc.get("reference").asString()).startsWith("MOCK-");}
        assertThat(call("POST","/reservations/"+id+"/cancellations",token,key(),Map.of("consent",true)).statusCode()).isEqualTo(409);
        clock.now=FlightFixture.flight(flight).checkInCloses();
        assertThat(call("POST","/reservations/"+id+"/check-in",token,key(),Map.of("passengerIds",List.of("P2"),"consent",true)).statusCode()).isEqualTo(409);
        assertThat(effects(UUID.fromString(id))).isEqualTo(1);
    }
    @Test void httpOwnershipScopesConsentAndInputBoundaries()throws Exception {
        assertThat(call("GET","/capabilities",null,null,null).statusCode()).isEqualTo(401);
        String noScope=issuer.token(identity.subject(),"");assertThat(call("GET","/capabilities",noScope,null,null).statusCode()).isEqualTo(403);
        assertThat(call("HEAD","/capabilities",noScope,null,null).statusCode()).isEqualTo(403);
        assertThat(call("POST","/capabilities",token,key(),Map.of()).statusCode()).isEqualTo(403);
        var q=quote("2A");var r=reserve(q);String bob=issuer.token("bob-"+key(),"bookings:read bookings:write");
        for(String path:List.of("/quotes/"+q.id(),"/reservations/"+r.id(),"/reservations/"+r.id()+"/documents"))assertThat(call("GET",path,bob,null,null).statusCode()).isEqualTo(404);
        assertThat(call("POST","/reservations/"+r.id()+"/payments",bob,key(),Map.of("outcome","SUCCEED","consent",true)).statusCode()).isEqualTo(404);
        assertThat(call("POST","/reservations/"+r.id()+"/payments",token,key(),Map.of("outcome","SUCCEED","consent",false)).statusCode()).isEqualTo(422);
        assertThat(call("POST","/reservations/"+r.id()+"/payments",token,key(),Map.of("outcome","SUCCEED","consent",true,"cardNumber","NEVER-ACCEPT-CARD-DATA")).statusCode()).isEqualTo(400);
        assertThat(call("POST","/reservations/"+r.id()+"/payments",token,key(),Map.of("outcome","SUCCEED","consent",true,"oversized","x".repeat(17000))).statusCode()).isEqualTo(413);
        assertThat(call("POST","/quotes",token,"short",Map.of("flightId",flight,"selections",List.of(selection("2B")))).statusCode()).isEqualTo(422);
        assertThat(call("POST","/reservations",token,key(),Map.of("quoteId",q.id(),"quoteVersion",1,"acceptedTotal",Map.of("amount","5.65E3","currency","INR"),"consent",true)).statusCode()).isEqualTo(400);
        assertThat(call("POST","/reservations/"+r.id()+"/check-in",token,key(),Map.of("passengerIds",List.of("P1","P1"),"consent",true)).statusCode()).isEqualTo(409);
        assertThat(effects(r.id())).isZero();
    }
    @Test void persistedCustomerSessionRequiresCsrfAndExpiredIdentityCannotReadProvider()throws Exception {
        for(boolean expired:List.of(false,true)){
            var oidc=new org.springframework.security.oauth2.core.oidc.OidcIdToken("test-session-only",Instant.now().minusSeconds(600),Instant.now().plusSeconds(expired?-60:300),Map.of("iss",issuer.url(),"sub",identity.subject(),"aud",List.of("travbook-web")));
            var authorities=List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_bookings:read"),new org.springframework.security.core.authority.SimpleGrantedAuthority("SCOPE_bookings:write"));
            var auth=new org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken(new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(authorities,oidc),authorities,"travbook");
            var id=saveSession(sessions,auth);String cookie="TRAVBOOK_SESSION="+Base64.getEncoder().encodeToString(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var http=HttpClient.newHttpClient();String base="http://127.0.0.1:"+port;
            var read=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/demo/flight-provider/capabilities")).header("Cookie",cookie).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(read.statusCode()).as(read.body()).isEqualTo(expired?401:200);assertThat(read.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
            if(!expired){
                String body=json.writeValueAsString(Map.of("flightId",flight,"selections",List.of(selection("2A"))));
                var denied=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/demo/flight-provider/quotes")).header("Cookie",cookie).header("Content-Type","application/json").header("Idempotency-Key",key()).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(denied.statusCode()).isEqualTo(403);
                var session=http.send(HttpRequest.newBuilder(URI.create(base+"/auth/session")).header("Cookie",cookie).GET().build(),HttpResponse.BodyHandlers.ofString());assertThat(session.statusCode()).isEqualTo(200);var csrf=json.readTree(session.body());
                var accepted=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/demo/flight-provider/quotes")).header("Cookie",cookie).header(csrf.get("csrfHeaderName").asString(),csrf.get("csrfToken").asString()).header("Content-Type","application/json").header("Idempotency-Key",key()).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(201);assertThat(json.readTree(accepted.body()).get("data").get("owner").asString()).isEqualTo(owner.toString());sessions.deleteById(id);
            }else assertThat(sessions.findById(id)).isNull();
        }
    }
    private <S extends org.springframework.session.Session> String saveSession(org.springframework.session.SessionRepository<S> repository,org.springframework.security.core.Authentication auth){var session=repository.createSession();session.setAttribute("SPRING_SECURITY_CONTEXT",new org.springframework.security.core.context.SecurityContextImpl(auth));repository.save(session);return session.getId();}
    @Test void httpCancellationAndRefundExposePendingStateAndOwnedCurrentResources()throws Exception {
        var q=quote("2A");var r=reserve(q);String path="/reservations/"+r.id();
        var history=checked(call("GET","/reservations",token,null,null),200);assertThat(history.get("owner").asString()).isEqualTo(owner.toString());assertThat(history.get("items").size()).isEqualTo(1);
        String bob=issuer.token("history-other-"+key(),"bookings:read");assertThat(call("GET","/reservations?after="+r.id(),bob,null,null).statusCode()).isEqualTo(404);
        checked(call("GET","/quotes/"+q.id(),token,null,null),200);checked(call("GET",path,token,null,null),200);
        var promotion=call("GET","/quotes/"+q.id()+"/promotion",token,null,null);assertThat(promotion.statusCode()).isEqualTo(200);assertThat(json.readTree(promotion.body()).get("promotion").isNull()).isTrue();
        checked(call("POST",path+"/payments",token,key(),Map.of("outcome","DECLINE","consent",true)),200);
        checked(call("POST",path+"/payments",token,key(),Map.of("outcome","SUCCEED","consent",true)),200);
        checked(call("POST",path+"/tickets",token,key(),Map.of("outcome","REJECT","consent",true)),200);
        var cancelled=checked(call("POST",path+"/cancellations",token,key(),Map.of("consent",true)),200);
        assertThat(cancelled.get("reservation").asString()).isEqualTo("CANCELLED");assertThat(cancelled.get("refund").asString()).isEqualTo("PENDING");
        var refunded=checked(call("POST",path+"/refunds",token,key(),Map.of("consent",true)),200);assertThat(refunded.get("payment").asString()).isEqualTo("REFUNDED");
        assertThat(effects(r.id())).isEqualTo(2);assertThat(call("GET",path+"/documents",token,null,null).statusCode()).isEqualTo(409);
    }
    @Test void expiredQuotesAndUnacceptedPricesCannotAllocateSeatsAndQuoteReplayIsImmutable(){
        String quoteKey=key();var q=provider.quote(identity,flight,List.of(selection("2A")),quoteKey,UUID.randomUUID());
        assertThatThrownBy(()->provider.reserve(identity,q.id(),1,TravelMoney.inr(1),true,key(),UUID.randomUUID())).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->provider.reserve(identity,q.id(),2,q.total(),true,key(),UUID.randomUUID())).isInstanceOf(DomainFailure.class);
        clock.now=q.expiresAt();assertThatThrownBy(()->reserve(q)).isInstanceOf(DomainFailure.class).hasMessageContaining("expired");
        assertThat(store.occupied(flight)).isEmpty();clock.now=q.flight().departure().plusSeconds(1);
        assertThat(provider.quote(identity,flight,List.of(selection("2A")),quoteKey,UUID.randomUUID())).isEqualTo(q);
        assertThatThrownBy(()->provider.quote(identity,flight,List.of(selection("2B")),quoteKey,UUID.randomUUID())).isInstanceOf(DomainFailure.class);
    }
    @Test void seatContentionAllowsExactlyOneReservationAndCancellationReleasesIt()throws Exception {
        var q1=quote("2A");var q2=quote("2A");
        try(var pool=Executors.newFixedThreadPool(2)){
            var results=pool.invokeAll(List.<Callable<ProviderReservation>>of(()->reserve(q1),()->reserve(q2)));
            var winners=new ArrayList<ProviderReservation>();int conflicts=0;
            for(var result:results)try{winners.add(result.get());}catch(ExecutionException e){assertThat(e.getCause()).isInstanceOf(DomainFailure.class);conflicts++;}
            assertThat(winners).hasSize(1);assertThat(conflicts).isEqualTo(1);assertThat(store.occupied(flight)).containsExactly("2A");
            act(winners.getFirst(),CANCEL);assertThat(store.occupied(flight)).isEmpty();reserve(quote("2A"));
        }
    }
    @Test void concurrentDuplicatesProduceOneReservationCaptureAndBalancedJournal()throws Exception {
        var q=quote("2A");String reserveKey=key(),payKey=key();
        try(var pool=Executors.newFixedThreadPool(4)){
            var commands=new ArrayList<Callable<ProviderReservation>>();for(int i=0;i<4;i++)commands.add(()->provider.reserve(identity,q.id(),1,q.total(),true,reserveKey,UUID.randomUUID()));
            var results=pool.invokeAll(commands);var r=results.getFirst().get();for(var result:results)assertThat(result.get()).isEqualTo(r);
            commands.clear();for(int i=0;i<4;i++)commands.add(()->provider.act(identity,r.id(),PAY_SUCCESS,List.of(),true,payKey,UUID.randomUUID()));
            for(var result:pool.invokeAll(commands))assertThat(result.get().payment()).isEqualTo(ProviderReservation.PaymentState.CAPTURED);
            assertThat(effects(r.id())).isEqualTo(1);
            assertThatThrownBy(()->provider.act(identity,r.id(),PAY_DECLINE,List.of(),true,payKey,UUID.randomUUID())).isInstanceOf(DomainFailure.class);
            assertThatThrownBy(()->act(r,PAY_SUCCESS)).isInstanceOf(DomainFailure.class);
            assertThat(jdbc.sql("select sum(case when j.side='DEBIT' then j.amount else -j.amount end) from fp_journal j join fp_money_effect e on e.id=j.effect_id where e.reservation_id=:id").param("id",r.id()).query(java.math.BigDecimal.class).single()).isZero();
            var replay=provider.reserve(identity,q.id(),1,q.total(),true,reserveKey,UUID.randomUUID());assertThat(replay.payment()).isEqualTo(ProviderReservation.PaymentState.UNPAID);
            assertThat(provider.reservation(identity,r.id()).payment()).isEqualTo(ProviderReservation.PaymentState.CAPTURED);
            assertThatThrownBy(()->reserve(q)).isInstanceOf(DomainFailure.class);
        }
    }
    @Test void rejectedTicketNeverProducesDocumentsAndRefundIsSeparateAndIdempotent(){
        var r=reserve(quote("2A"));r=act(r,PAY_DECLINE);assertThat(r.payment()).isEqualTo(ProviderReservation.PaymentState.DECLINED);
        r=act(r,PAY_SUCCESS);r=act(r,TICKET_REJECT);assertThat(r.ticket()).isEqualTo(ProviderReservation.TicketState.REJECTED);final var rejected=r;
        assertThatThrownBy(()->provider.documents(identity,rejected.id())).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->act(rejected,REFUND)).isInstanceOf(DomainFailure.class);
        r=act(r,CANCEL);assertThat(r.refund()).isEqualTo(ProviderReservation.RefundState.PENDING);assertThat(r.payment()).isEqualTo(ProviderReservation.PaymentState.CAPTURED);
        String refundKey=key();r=provider.act(identity,r.id(),REFUND,List.of(),true,refundKey,UUID.randomUUID());
        assertThat(r.refund()).isEqualTo(ProviderReservation.RefundState.COMPLETED);assertThat(effects(r.id())).isEqualTo(2);
        assertThat(provider.act(identity,r.id(),REFUND,List.of(),true,refundKey,UUID.randomUUID())).isEqualTo(r);final var refunded=r;
        assertThatThrownBy(()->act(refunded,REFUND)).isInstanceOf(DomainFailure.class);assertThat(store.occupied(flight)).isEmpty();
    }
    @Test void eventFailureRollsBackPaymentReceiptAndJournalTogether(){
        var r=reserve(quote("2A"));String payKey=key();
        jdbc.sql("CREATE FUNCTION fp_event_failure_fixture() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture failure'; END; $$").update();
        jdbc.sql("CREATE TRIGGER fp_event_failure BEFORE INSERT ON fp_event FOR EACH ROW EXECUTE FUNCTION fp_event_failure_fixture()").update();
        try{assertThatThrownBy(()->provider.act(identity,r.id(),PAY_SUCCESS,List.of(),true,payKey,UUID.randomUUID())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(effects(r.id())).isZero();assertThat(provider.reservation(identity,r.id()).payment()).isEqualTo(ProviderReservation.PaymentState.UNPAID);}
        finally{jdbc.sql("DROP TRIGGER fp_event_failure ON fp_event").update();jdbc.sql("DROP FUNCTION fp_event_failure_fixture()").update();}
        assertThat(provider.act(identity,r.id(),PAY_SUCCESS,List.of(),true,payKey,UUID.randomUUID()).payment()).isEqualTo(ProviderReservation.PaymentState.CAPTURED);
        assertThat(effects(r.id())).isEqualTo(1);
    }
    @Test void actualProcessCrashAfterCaptureCanBeRetriedWithoutAnotherEffect()throws Exception {
        var r=reserve(quote("2A"));String payKey=key();var log=temp.resolve("provider-crash.log");
        var cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",cp,"com.travbook.support.FlightProviderProcess",identity.issuer(),identity.subject(),r.id().toString(),payKey,clock.now.toString()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("DATABASE_URL",postgres.getJdbcUrl());builder.environment().put("DATABASE_USERNAME",postgres.getUsername());builder.environment().put("DATABASE_PASSWORD",postgres.getPassword());
        var child=builder.start();boolean exited=child.waitFor(40,TimeUnit.SECONDS);if(!exited)child.destroyForcibly();assertThat(exited).as(Files.readString(log)).isTrue();assertThat(child.exitValue()).as(Files.readString(log)).isEqualTo(34);
        assertThat(effects(r.id())).isEqualTo(1);var recovered=provider.act(identity,r.id(),PAY_SUCCESS,List.of(),true,payKey,UUID.randomUUID());
        assertThat(recovered.payment()).isEqualTo(ProviderReservation.PaymentState.CAPTURED);assertThat(effects(r.id())).isEqualTo(1);
        assertThat(jdbc.sql("select version from fp_event where aggregate_id=:id order by version").param("id",r.id()).query(Long.class).list()).containsExactly(1L,2L);
    }
    @Test void journalAndQuoteHistoryCannotBeSilentlyAltered(){
        var q=quote("2A");var r=reserve(q);
        assertThatThrownBy(()->jdbc.sql("delete from fp_quote where id=:id").param("id",q.id()).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.sql("insert into fp_money_effect(id,reservation_id,kind,amount,reference,occurred_at) values(:id,:r,'CAPTURE',1,'MOCK-INVALID',now())").param("id",UUID.randomUUID()).param("r",r.id()).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(effects(r.id())).isZero();final var paid=act(r,PAY_SUCCESS);
        assertThatThrownBy(()->jdbc.sql("update fp_money_effect set amount=1 where reservation_id=:id").param("id",paid.id()).update()).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void passengerEligibilityAndAttemptsAreBounded(){
        assertThatThrownBy(()->new FlightFixture.Selection("P1",17,"3A",FlightFixture.Meal.NONE,FlightFixture.Baggage.NONE)).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->provider.quote(identity,flight,List.of(new FlightFixture.Selection("P1",10,"2A",FlightFixture.Meal.NONE,FlightFixture.Baggage.NONE)),key(),UUID.randomUUID())).isInstanceOf(DomainFailure.class);
        assertThatThrownBy(()->provider.quote(identity,flight,List.of(selection("2A"),selection("2B")),key(),UUID.randomUUID())).isInstanceOf(DomainFailure.class);
        var r=reserve(quote("2A"));for(int i=0;i<10;i++)r=act(r,PAY_DECLINE);final var exhausted=r;
        assertThatThrownBy(()->act(exhausted,PAY_SUCCESS)).isInstanceOf(DomainFailure.class);assertThat(effects(r.id())).isZero();act(r,CANCEL);
    }
    @Test void reservationHistoryUsesOwnedStableCursorPages(){
        var expected=new HashSet<UUID>();var first=clock.now.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().plusDays(1);
        for(int i=0;i<22;i++){flight="MOCK-FA-"+first.plusDays(i);expected.add(reserve(quote("2A")).id());}
        var page=provider.reservations(identity,null);assertThat(page.items()).hasSize(20);assertThat(page.next()).isNotNull();var second=provider.reservations(identity,page.next());assertThat(second.items()).hasSize(2);assertThat(second.next()).isNull();
        var actual=new HashSet<UUID>();page.items().forEach(r->actual.add(r.id()));second.items().forEach(r->actual.add(r.id()));assertThat(actual).isEqualTo(expected);
        var foreign=new CustomerIdentity(issuer.url(),key());assertThatThrownBy(()->provider.reservations(foreign,page.next())).isInstanceOf(DomainFailure.class);
    }
}
