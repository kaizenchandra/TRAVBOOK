package com.travbook;
import com.travbook.simulation.application.port.in.*;
import com.travbook.simulation.application.port.out.*;
import com.travbook.simulation.domain.model.*;
import com.travbook.customer.application.port.in.*;
import com.travbook.customer.domain.model.*;
import com.travbook.customer.domain.model.Profile;
import com.travbook.payments.application.port.in.*;
import com.travbook.payments.domain.model.Payment;
import com.travbook.pricing.application.port.in.Quotations;
import com.travbook.search.application.port.in.DiscoverFlights;
import com.travbook.search.domain.model.FlightQuery;
import com.travbook.journeys.application.port.in.*;
import com.travbook.journeys.domain.model.*;
import com.travbook.cabs.application.port.in.DiscoverCabs;
import com.travbook.cabs.domain.model.Cab;
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
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"travbook.demo.enabled=true","travbook.demo.worker-enabled=false","JOURNEY_WORKER_ENABLED=false","JOURNEY_CARE_WORKER_ENABLED=false","travbook.payments.refund-worker-enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(DemoIntegrationTest.TimeConfig.class)
@Testcontainers
class DemoIntegrationTest {
    static final TestIssuer issuer=new TestIssuer();
    @Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);}
    @TestConfiguration static class TimeConfig{@Bean @Primary MutableClock demoClock(){return new MutableClock();}}
    static class MutableClock extends Clock{Instant now=Instant.now();public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return Clock.fixed(now,z);}public Instant instant(){return now;}}
    @Autowired DemoServices demo;@Autowired DemoStore store;@Autowired DemoDelivery delivery;@Autowired ProcessDemoOperations worker;
    @Autowired Customers customers;@Autowired CustomerProfiles profiles;@Autowired PaymentOperations payments;@Autowired PaymentRefunds refunds;
    @Autowired Quotations quotes;@Autowired DiscoverFlights flights;@Autowired DiscoverCabs cabs;@Autowired AdvancedJourneys journeys;@Autowired ProcessJourneys journeyWorker;
    @Autowired MutableClock clock;@Autowired JdbcClient jdbc;@Autowired ObjectMapper json;@LocalServerPort int port;@TempDir Path temp;
    CustomerIdentity identity;UUID owner;String token;
    @BeforeEach void init()throws Exception{clock.now=clock.now.plusSeconds(100);identity=new CustomerIdentity(issuer.url(),"demo-"+UUID.randomUUID());owner=customers.resolve(identity).id();token=issuer.token(identity.subject(),"quotes:read quotes:write bookings:read bookings:write");}
    @AfterEach void drain(){clock.now=clock.now.plusSeconds(60);for(int i=0;i<150;i++){clock.now=clock.now.plusSeconds(10);if(!worker.tick())break;}}
    @AfterAll static void stop(){issuer.close();}
    String key(){return UUID.randomUUID().toString();}
    DemoOperation create(DemoOperation.Kind kind,DemoOperation.Scenario scenario,String key){return demo.create(identity,owner,kind,scenario,true,key,UUID.randomUUID());}
    DemoOperation finish(DemoOperation op){for(int i=0;i<15;i++){var current=demo.get(identity,op.id());if(current.terminal())return current;worker.tick();clock.now=clock.now.plusSeconds(5);}throw new AssertionError("Simulation did not finish");}
    long receipts(UUID id){return jdbc.sql("select count(*) from demo_delivery_receipt where operation_id=:id").param("id",id).query(Long.class).single();}
    HttpResponse<String> call(String method,String path,String auth,String key,Object body)throws Exception{
        var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));if(auth!=null)b.header("Authorization","Bearer "+auth);if(key!=null)b.header("Idempotency-Key",key);
        if(body==null)b.method(method,HttpRequest.BodyPublishers.noBody());else b.header("Content-Type","application/json").method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());
    }
    JsonNode checked(HttpResponse<String> r,int status){assertThat(r.statusCode()).as(r.body()).isEqualTo(status);return json.readTree(r.body());}
    @Test void actualDummyHttpOperationsEnforceOwnerScopeAndConsent()throws Exception{
        assertThat(call("GET","/api/v1/demo/catalog",null,null,null).statusCode()).isEqualTo(401);
        var catalog=checked(call("GET","/api/v1/demo/catalog",token,null,null),200);assertThat(catalog.get("services").size()).isEqualTo(7);
        var deployment=checked(call("GET","/api/v1/demo/deployment",token,null,null),200);assertThat(deployment.get("awsDeployed").asBoolean()).isFalse();
        String command=key();var body=Map.of("expectedCustomerId",owner,"kind","EMAIL","scenario","NORMAL","consent",true);
        var first=checked(call("POST","/api/v1/demo/operations",token,command,body),202);UUID id=UUID.fromString(first.get("id").asString());
        assertThat(first.get("status").asString()).isEqualTo("QUEUED");assertThat(first.get("reference").isNull()).isTrue();
        assertThat(checked(call("POST","/api/v1/demo/operations",token,command,body),202).get("id").asString()).isEqualTo(id.toString());
        String bob=issuer.token("bob-"+key(),"quotes:read bookings:read");assertThat(call("GET","/api/v1/demo/operations/"+id,bob,null,null).statusCode()).isEqualTo(404);
        assertThat(call("GET","/api/v1/demo/operations?after="+id,bob,null,null).statusCode()).isEqualTo(404);
        assertThat(call("POST","/api/v1/demo/operations",bob,key(),body).statusCode()).isEqualTo(409);
        assertThat(call("POST","/api/v1/demo/operations",token,key(),Map.of("expectedCustomerId",owner,"kind","SMS","scenario","NORMAL","consent",false)).statusCode()).isEqualTo(422);
        var injected=new HashMap<String,Object>(body);injected.put("recipient","outside@example.test");assertThat(call("POST","/api/v1/demo/operations",token,key(),injected).statusCode()).isEqualTo(400);
        String noScope=issuer.token(identity.subject(),"");assertThat(call("GET","/api/v1/demo/financial-report",noScope,null,null).statusCode()).isEqualTo(403);
        assertThat(call("HEAD","/api/v1/demo/financial-report",noScope,null,null).statusCode()).isEqualTo(403);
        assertThat(call("POST","/api/v1/demo/financial-report",token,key(),Map.of()).statusCode()).isEqualTo(403);
        assertThat(finish(demo.get(identity,id)).status()).isEqualTo(DemoOperation.State.SIMULATED_DELIVERED);assertThat(receipts(id)).isEqualTo(1);
        var done=demo.get(identity,id);var wrongOwner=new DemoOperation(done.id(),UUID.randomUUID(),done.kind(),done.scenario(),done.status(),done.attempts(),done.version(),done.createdAt(),done.updatedAt(),done.nextAttemptAt(),done.reference(),done.correlation());
        assertThatThrownBy(()->delivery.accepted(wrongOwner)).isInstanceOf(IllegalStateException.class);
    }
    @Test void retriesAreBoundedAndReceiptIsNeverManufacturedForFailedDelivery(){
        var retry=create(DemoOperation.Kind.SMS,DemoOperation.Scenario.RETRY_ONCE,key());var done=finish(retry);
        assertThat(done.attempts()).isEqualTo(2);assertThat(done.status()).isEqualTo(DemoOperation.State.SIMULATED_DELIVERED);assertThat(receipts(done.id())).isEqualTo(1);
        var failed=finish(create(DemoOperation.Kind.EMAIL,DemoOperation.Scenario.FAIL,key()));assertThat(failed.attempts()).isEqualTo(3);assertThat(failed.status()).isEqualTo(DemoOperation.State.FAILED);assertThat(failed.reference()).isNull();assertThat(receipts(failed.id())).isZero();
    }
    @Test void concurrentCommandsAndWorkersCreateOneOperationAndReceipt()throws Exception{
        String key=key();try(var pool=Executors.newFixedThreadPool(4)){
            var tasks=new ArrayList<Callable<DemoOperation>>();for(int i=0;i<4;i++)tasks.add(()->create(DemoOperation.Kind.EMAIL,DemoOperation.Scenario.NORMAL,key));
            var results=pool.invokeAll(tasks);UUID id=results.getFirst().get().id();for(var result:results)assertThat(result.get().id()).isEqualTo(id);
            assertThatThrownBy(()->create(DemoOperation.Kind.SMS,DemoOperation.Scenario.NORMAL,key)).isInstanceOf(DomainFailure.class);
            var work=pool.invokeAll(List.<Callable<Boolean>>of(worker::tick,worker::tick,worker::tick,worker::tick));for(var result:work)result.get();
            assertThat(demo.get(identity,id).terminal()).isTrue();assertThat(receipts(id)).isEqualTo(1);
        }
    }
    @Test void eventFailureRollsBackAcceptedCommandAndLeavesKeyReusable(){
        jdbc.sql("CREATE FUNCTION reject_demo_event_fixture() RETURNS TRIGGER LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'event unavailable'; END; $$").update();
        jdbc.sql("CREATE TRIGGER demo_event_fixture BEFORE INSERT ON demo_event FOR EACH ROW EXECUTE FUNCTION reject_demo_event_fixture()").update();String key=key();
        try{assertThatThrownBy(()->create(DemoOperation.Kind.EMAIL,DemoOperation.Scenario.NORMAL,key)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(demo.list(identity,null).items()).isEmpty();}
        finally{jdbc.sql("DROP TRIGGER demo_event_fixture ON demo_event").update();jdbc.sql("DROP FUNCTION reject_demo_event_fixture()").update();}
        assertThat(finish(create(DemoOperation.Kind.EMAIL,DemoOperation.Scenario.NORMAL,key)).terminal()).isTrue();
    }
    @Test void actualProcessCrashAfterProviderCommitRecoversOneReceiptAndRejectsOldFence()throws Exception{
        var op=create(DemoOperation.Kind.EMAIL,DemoOperation.Scenario.NORMAL,key());var claim=store.claim(clock.instant()).orElseThrow();assertThat(claim.operation().id()).isEqualTo(op.id());
        var log=temp.resolve("delivery-crash.log");var cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",cp,"com.travbook.support.DemoDeliveryProcess",owner.toString(),op.id().toString()).redirectErrorStream(true).redirectOutput(log.toFile());
        builder.environment().put("DATABASE_URL",postgres.getJdbcUrl());builder.environment().put("DATABASE_USERNAME",postgres.getUsername());builder.environment().put("DATABASE_PASSWORD",postgres.getPassword());
        var child=builder.start();boolean exited=child.waitFor(35,TimeUnit.SECONDS);if(!exited)child.destroyForcibly();assertThat(exited).isTrue();assertThat(child.exitValue()).as(Files.readString(log)).isEqualTo(33);
        assertThat(receipts(op.id())).isEqualTo(1);clock.now=clock.now.plusSeconds(16);
        assertThat(store.complete(claim,claim.operation().complete(DemoOperation.Outcome.ACCEPTED,clock.instant()))).isFalse();
        assertThat(finish(op).status()).isEqualTo(DemoOperation.State.SIMULATED_DELIVERED);assertThat(receipts(op.id())).isEqualTo(1);
        assertThat(jdbc.sql("select version from demo_event where operation_id=:id order by version").param("id",op.id()).query(Long.class).list()).containsExactly(1L,2L,3L,4L);
    }
    @Test void paginatedHistoryHasOwnedStableCursors(){
        var ids=new HashSet<UUID>();for(int i=0;i<23;i++)ids.add(create(DemoOperation.Kind.EMAIL,DemoOperation.Scenario.NORMAL,key()).id());
        var first=demo.list(identity,null);var second=demo.list(identity,first.next());assertThat(first.items()).hasSize(20);assertThat(second.items()).hasSize(3);assertThat(second.next()).isNull();
        var returned=new HashSet<UUID>();first.items().forEach(o->returned.add(o.id()));second.items().forEach(o->returned.add(o.id()));assertThat(returned).isEqualTo(ids);
    }
    @Test void finalLostAcknowledgementUsesOnlyReceiptLookup(){
        var op=create(DemoOperation.Kind.EMAIL,DemoOperation.Scenario.NORMAL,key());
        for(int i=0;i<3;i++){var claim=store.claim(clock.instant()).orElseThrow();assertThat(claim.lookupOnly()).isFalse();if(i==2)delivery.deliver(claim.operation());clock.now=clock.now.plusSeconds(16);}
        assertThat(worker.tick()).isTrue();var done=demo.get(identity,op.id());assertThat(done.status()).isEqualTo(DemoOperation.State.SIMULATED_DELIVERED);assertThat(done.attempts()).isEqualTo(3);assertThat(receipts(op.id())).isEqualTo(1);
    }
    @Test void uncertainReceiptBindingEndsInReviewWithoutInventedSuccess(){
        var op=create(DemoOperation.Kind.EMAIL,DemoOperation.Scenario.NORMAL,key());
        jdbc.sql("insert into demo_delivery_receipt(operation_id,fingerprint,accepted_at) values(:id,'wrong-binding',now())").param("id",op.id()).update();
        for(int i=0;i<3;i++){assertThatThrownBy(worker::tick).isInstanceOf(IllegalStateException.class);clock.now=clock.now.plusSeconds(16);}
        var review=demo.get(identity,op.id());assertThat(review.status()).isEqualTo(DemoOperation.State.SIMULATED_REVIEW_REQUIRED);assertThat(review.reference()).isNull();assertThat(review.attempts()).isEqualTo(3);assertThat(worker.tick()).isFalse();
    }
    @Test void privacyPreviewIsIncompleteAndSimulatedErasureDoesNotAlterData()throws Exception{
        profiles.save(identity,new ProfileChange(owner,0,new Profile.Fields("Sample Profile","sample@example.test",""),true),key(),UUID.randomUUID());var saved=profiles.get(identity);
        var preview=checked(call("GET","/api/v1/demo/privacy-preview",token,null,null),200);assertThat(preview.get("completeExport").asBoolean()).isFalse();assertThat(preview.get("excluded").size()).isGreaterThan(0);
        for(var kind:List.of(DemoOperation.Kind.PRIVACY_ERASURE,DemoOperation.Kind.PRIVACY_EXPORT))assertThat(finish(create(kind,DemoOperation.Scenario.NORMAL,key())).status()).isEqualTo(DemoOperation.State.SIMULATED_REVIEW_REQUIRED);
        assertThat(profiles.get(identity)).isEqualTo(saved);
        String events=jdbc.sql("select e.envelope::text from demo_event e join demo_operation o on o.id=e.operation_id where o.owner_id=:owner").param("owner",owner).query(String.class).list().toString();assertThat(events).doesNotContain("sample@example.test","Sample Profile","displayName");
    }
    @Test void couponsUseOwnedCurrentQuotesAndNeverMutateAcceptedTotals()throws Exception{
        var search=flights.search(new FlightQuery("DEL","BOM",LocalDate.now(clock).plusDays(7),null,1,0,0,FlightQuery.Cabin.ECONOMY));
        var quote=quotes.create(identity,search.offers().getFirst().id(),key(),UUID.randomUUID());quotes.accept(identity,quote.id(),1,key(),UUID.randomUUID());
        var before=quotes.get(identity,quote.id());var eligible=demo.coupon(identity,quote.id(),"demo10");assertThat(eligible.coupon().eligible()).isTrue();
        assertThat(eligible.coupon().discount().decimal()).isEqualByComparingTo(quote.selection().total().amount().multiply(new BigDecimal("0.10")).min(new BigDecimal("500")));
        assertThat(demo.coupon(identity,quote.id(),"DEMOEXPIRED").coupon().reason()).isEqualTo("EXPIRED_CODE");assertThat(demo.coupon(identity,quote.id(),"unknown").coupon().eligible()).isFalse();
        assertThat(DemoCoupon.preview("DEMO10",new BigDecimal("500")).reason()).isEqualTo("MINIMUM_NOT_MET");assertThat(quotes.get(identity,quote.id())).isEqualTo(before);
        var foreign=new CustomerIdentity(issuer.url(),"foreign-"+key());assertThatThrownBy(()->demo.coupon(foreign,quote.id(),"DEMO10")).isInstanceOf(DomainFailure.class);
        clock.now=quote.expiresAt();assertThatThrownBy(()->demo.coupon(identity,quote.id(),"DEMO10")).isInstanceOf(DomainFailure.class);
    }
    @Test void paymentAndReportsUseOwnedActualMockLedgerWithoutExternalSettlement()throws Exception{
        UUID payment=UUID.randomUUID();var total=new BigDecimal("1234.50");var until=clock.instant().plusSeconds(600);
        payments.execute(new PaymentOperations.Command(UUID.randomUUID(),payment,owner,Payment.Operation.AUTHORIZE,total,"INR",until,false));
        payments.execute(new PaymentOperations.Command(UUID.randomUUID(),payment,owner,Payment.Operation.CAPTURE,total,"INR",until,false));
        UUID refund=UUID.randomUUID();refunds.submit(new PaymentRefunds.Command(refund,payment,owner,UUID.randomUUID(),new BigDecimal("200.00"),"INR",false,UUID.randomUUID(),UUID.randomUUID()));
        assertThat(demo.report(identity).snapshot().pendingRefund()).isEqualByComparingTo("200");assertThat(refunds.settleNext()).isTrue();
        var report=checked(call("GET","/api/v1/demo/financial-report",token,null,null),200);assertThat(report.get("capturedAmount").asString()).isEqualTo("1234.50");assertThat(report.get("refundedAmount").asString()).isEqualTo("200.00");assertThat(report.get("netCaptured").asString()).isEqualTo("1034.50");assertThat(report.get("integrityIssues").asLong()).isZero();assertThat(report.get("settlementStatus").asString()).isEqualTo("NOT_RECONCILED");
        assertThat(checked(call("GET","/api/v1/demo/payments/"+payment,token,null,null),200).get("state").asString()).isEqualTo("CAPTURED");
        String other=issuer.token("report-other-"+key(),"bookings:read");assertThat(call("GET","/api/v1/demo/payments/"+payment,other,null,null).statusCode()).isEqualTo(404);
        assertThat(checked(call("GET","/api/v1/demo/financial-report",other,null,null),200).get("capturedAmount").asString()).isEqualTo("0.00");
    }
    @Test void gpsFollowsOwnedDurableDispatchAndRemovesPositionAfterCancellation(){
        var s=cabs.search(new Cab.Criteria(Cab.Type.AIRPORT,Cab.Place.DELHI_AIRPORT,Cab.Place.DELHI_CENTRE,LocalDate.now(clock).plusDays(20),"10:00",null,null,1,1,0));
        var request=new Journey.Request(Journey.Kind.CAB,s.id(),s.items().getFirst().id(),List.of(),null,null);
        var q=journeys.quote(identity,request,Care.Plan.FULL,key(),UUID.randomUUID());journeys.accept(identity,q.details().quote().id(),2,true,key(),UUID.randomUUID());
        var b=journeys.book(identity,q.details().quote().id(),List.of(new Journey.Passenger("Sample Person",30)),"sample@example.test",Journey.Scenario.NORMAL,key(),UUID.randomUUID());journeys.pay(identity,b.booking().id(),key(),UUID.randomUUID());
        for(int i=0;i<30&&journeys.get(identity,b.booking().id()).booking().status()!=Journey.Status.CONFIRMED;i++){journeyWorker.tick();clock.now=clock.now.plusMillis(100);}
        UUID id=b.booking().id();var gps=demo.gps(identity,id);assertThat(gps.latitude()).isEqualTo("28.550000");assertThat(gps.stale()).isFalse();
        clock.now=clock.now.plusSeconds(121);assertThat(demo.gps(identity,id).stale()).isTrue();journeys.advanceDemo(identity,id,gps.dispatch().version(),true,key(),UUID.randomUUID());assertThat(demo.gps(identity,id).latitude()).isEqualTo("28.552000");
        assertThatThrownBy(()->demo.gps(new CustomerIdentity(issuer.url(),"gps-foreign-"+key()),id)).isInstanceOf(DomainFailure.class);
        var cancel=journeys.cancellationQuote(identity,id,journeys.get(identity,id).activeComponentIds(),key(),UUID.randomUUID());journeys.cancel(identity,id,cancel.id(),true,false,false,key(),UUID.randomUUID());
        assertThat(demo.gps(identity,id).latitude()).isNull();assertThat(demo.gps(identity,id).terminal()).isTrue();
    }
}
