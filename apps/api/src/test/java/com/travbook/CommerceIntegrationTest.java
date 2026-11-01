package com.travbook;
import com.travbook.flightprovider.application.port.in.FlightProvider;
import com.travbook.flightprovider.domain.model.*;
import com.travbook.customer.application.port.in.*;
import com.travbook.customer.domain.model.TravelOrganizer;
import com.travbook.offers.domain.model.Coupon;
import com.travbook.notifications.application.port.in.*;
import com.travbook.notifications.application.port.out.*;
import com.travbook.notifications.application.usecase.NotificationService;
import com.travbook.notifications.domain.model.*;
import com.travbook.shared.application.port.LifecycleSource;
import com.travbook.shared.domain.*;
import com.travbook.support.TestIssuer;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.*;
import java.time.*;import java.util.*;import java.util.concurrent.*;import java.net.*;import java.net.http.*;
import static org.assertj.core.api.Assertions.*;
import static com.travbook.flightprovider.domain.model.ProviderReservation.Action.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"travbook.demo.enabled=true","travbook.demo.worker-enabled=false","travbook.notifications.worker-enabled=false"})
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Import(CommerceIntegrationTest.TimeConfig.class) @Testcontainers
class CommerceIntegrationTest {
 static final TestIssuer issuer=new TestIssuer();@Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
 @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);}
 @TestConfiguration static class TimeConfig{@Bean @Primary MutableClock commerceClock(){return new MutableClock();}}
 static class MutableClock extends Clock{Instant now=Instant.now();public Instant instant(){return now;}public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return Clock.fixed(now,z);}}
 @Autowired FlightProvider flights;@Autowired Customers customers;@Autowired CustomerOrganizer organizer;@Autowired Notifications notices;@Autowired ProcessNotifications worker;@Autowired NotificationStore store;@Autowired NotificationGateway gateway;@Autowired List<LifecycleSource> sources;@Autowired JdbcClient jdbc;@Autowired ObjectMapper json;@Autowired MutableClock clock;@LocalServerPort int port;
 CustomerIdentity identity;UUID owner;String token,flight;static int sequence;
 @TempDir Path temporary;
 @BeforeEach void setup()throws Exception{clock.now=Instant.now().plusSeconds(1);identity=new CustomerIdentity(issuer.url(),UUID.randomUUID().toString());owner=customers.resolve(identity).id();token=issuer.token(identity.subject(),"bookings:read bookings:write");flight="MOCK-FA-"+clock.now.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate().plusDays(10+(++sequence*3));}
 @AfterEach void drain(){for(int i=0;i<50;i++){clock.now=clock.now.plusSeconds(5);try{worker.relay();if(!worker.deliver())break;}catch(RuntimeException ignored){}}}
 @AfterAll static void close(){issuer.close();}
 String key(){return UUID.randomUUID().toString();}
 FlightFixture.Selection selection(String seat){return new FlightFixture.Selection("P1",30,seat,FlightFixture.Meal.VEG,FlightFixture.Baggage.KG20);}
 ProviderQuote quote(String code,String seat){return flights.promotionalQuote(identity,owner,flight,List.of(selection(seat)),code,key(),UUID.randomUUID());}
 ProviderReservation reserve(ProviderQuote q){return flights.reserve(identity,q.id(),1,q.total(),true,key(),UUID.randomUUID());}
 ProviderReservation action(ProviderReservation r,ProviderReservation.Action a){return flights.act(identity,r.id(),a,List.of(),true,key(),UUID.randomUUID());}
 void prefs(boolean email,boolean sms){var old=organizer.get(identity);organizer.save(identity,owner,old.version(),new TravelOrganizer.Fields(List.of(),List.of(),List.of(),new TravelOrganizer.Preferences(email,sms,false)),true,key(),UUID.randomUUID());}
 HttpResponse<String> http(String method,String path,String bearer,String key,Object body)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path)).timeout(Duration.ofSeconds(10));if(bearer!=null)b.header("Authorization","Bearer "+bearer);if(key!=null)b.header("Idempotency-Key",key);if(body!=null)b.header("Content-Type","application/json");b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());}
 long count(String table,UUID id){return jdbc.sql("select count(*) from "+table+" where owner_id=:owner").param("owner",id).query(Long.class).single();}
 void campaign(String code,int global,int customer,Instant ends){var c=new Coupon(code,1,"FLIGHT_EXTRAS",10,TravelMoney.inr(500),TravelMoney.inr(1000),clock.instant().minusSeconds(100),ends,global,customer,null,true);jdbc.sql("insert into coupon_campaign(code,version,payload) values(:code,1,cast(:payload as jsonb))").param("code",code).param("payload",json.writeValueAsString(c)).update();}
 LifecycleSignal signal(UUID aggregate,long version,String status){return new LifecycleSignal(UUID.randomUUID(),owner,aggregate,aggregate,version,"flight-extras",Map.of("status",status),clock.instant(),"/flight-services?booking="+aggregate);}
 @Test void actualCouponHttpDiscountFlowsThroughCaptureAndRefundWithoutChangingOldQuotes()throws Exception{
  var catalog=http("GET","/api/v1/coupons",token,null,null);assertThat(catalog.statusCode()).isEqualTo(200);assertThat(json.readTree(catalog.body()).get("customerId").asString()).isEqualTo(owner.toString());
  var original=flights.quote(identity,flight,List.of(selection("2A")),key(),UUID.randomUUID());String command=key();var body=Map.of("expectedCustomerId",owner,"flightId",flight,"selections",List.of(selection("2A")),"couponCode"," travel10 ");
  var response=http("POST","/api/v2/demo/flight-provider/quotes",token,command,body);assertThat(response.statusCode()).as(response.body()).isEqualTo(201);var q=json.readValue(json.readTree(response.body()).get("data").toString(),ProviderQuote.class);assertThat(q.total().amount()).isEqualTo("5250.00");assertThat(q.lines().getFirst().base().amount()).isEqualTo("3600.00");assertThat(flights.quote(identity,original.id()).total().amount()).isEqualTo("5650.00");
  assertThat(json.readTree(http("POST","/api/v2/demo/flight-provider/quotes",token,command,body).body()).get("data").get("id").asString()).isEqualTo(q.id().toString());var promotion=flights.promotion(identity,q.id()).promotion();assertThat(promotion.discount().amount()).isEqualTo("400.00");assertThat(count("coupon_redemption",owner)).isZero();
  assertThat(http("GET","/api/v1/demo/flight-provider/quotes/"+q.id()+"/promotion",issuer.token("other-"+key(),"bookings:read"),null,null).statusCode()).isEqualTo(404);
  var r=reserve(q);assertThat(count("coupon_redemption",owner)).isEqualTo(1);r=action(r,PAY_SUCCESS);r=action(r,TICKET_ISSUE);r=action(r,CANCEL);r=action(r,REFUND);assertThat(r.payment()).isEqualTo(ProviderReservation.PaymentState.REFUNDED);
  assertThat(jdbc.sql("select amount from fp_money_effect where reservation_id=:id order by kind").param("id",r.id()).query(java.math.BigDecimal.class).list()).allSatisfy(a->assertThat(a).isEqualByComparingTo("5250.00"));assertThat(count("coupon_redemption",owner)).isEqualTo(1);
 }
 @Test void concurrentLastRedemptionHasOneWinnerAndNoPartialSeatAllocation()throws Exception{
  String code="LAST"+key().replace("-","").substring(0,8).toUpperCase();campaign(code,1,1,clock.instant().plusSeconds(3600));var q1=quote(code,"2A");var bob=new CustomerIdentity(issuer.url(),key());UUID other=customers.resolve(bob).id();var q2=flights.promotionalQuote(bob,other,flight,List.of(selection("2B")),code,key(),UUID.randomUUID());
  try(var pool=Executors.newFixedThreadPool(2)){var results=pool.invokeAll(List.<Callable<ProviderReservation>>of(()->reserve(q1),()->flights.reserve(bob,q2.id(),1,q2.total(),true,key(),UUID.randomUUID())));int success=0,fail=0;for(var result:results)try{result.get();success++;}catch(ExecutionException e){assertThat(e.getCause()).isInstanceOf(DomainFailure.class);fail++;}assertThat(success).isEqualTo(1);assertThat(fail).isEqualTo(1);}
  assertThat(jdbc.sql("select count(*) from coupon_redemption where code=:code").param("code",code).query(Long.class).single()).isEqualTo(1);assertThat(jdbc.sql("select count(*) from fp_seat where flight_id=:flight").param("flight",flight).query(Long.class).single()).isEqualTo(1);
 }
 @Test void expiredChangedUnknownAndExhaustedCouponsFailWithoutInventingAPrice(){
  assertThatThrownBy(()->quote("UNKNOWN","2A")).isInstanceOf(DomainFailure.class);assertThat(count("coupon_award",owner)).isZero();
  String code="SHORT"+key().replace("-","").substring(0,6).toUpperCase();campaign(code,10,1,clock.instant().plusSeconds(10));var q=quote(code,"2A");clock.now=q.expiresAt();assertThatThrownBy(()->reserve(q)).isInstanceOf(DomainFailure.class);assertThat(count("coupon_redemption",owner)).isZero();
  var welcome=quote("WELCOME15","2B");reserve(welcome);assertThatThrownBy(()->quote("WELCOME15","2C")).isInstanceOf(DomainFailure.class);
  var change=quote("TRAVEL10","2C");jdbc.sql("update coupon_campaign set version=2,payload=jsonb_set(payload,'{version}','2') where code='TRAVEL10'").update();try{assertThatThrownBy(()->reserve(change)).isInstanceOf(DomainFailure.class);}finally{jdbc.sql("update coupon_campaign set version=1,payload=jsonb_set(payload,'{version}','1') where code='TRAVEL10'").update();}
 }
 @Test void reservationEventFailureRollsBackCouponRedemptionAndOutbox(){
  var q=quote("TRAVEL10","2A");jdbc.sql("CREATE FUNCTION fail_promo_fixture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture failure'; END; $$").update();jdbc.sql("CREATE TRIGGER fail_promo_fixture BEFORE INSERT ON fp_event FOR EACH ROW EXECUTE FUNCTION fail_promo_fixture()").update();
  try{assertThatThrownBy(()->reserve(q)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(count("coupon_redemption",owner)).isZero();assertThat(jdbc.sql("select count(*) from fp_seat where flight_id=:flight").param("flight",flight).query(Long.class).single()).isZero();}finally{jdbc.sql("DROP TRIGGER fail_promo_fixture ON fp_event").update();jdbc.sql("DROP FUNCTION fail_promo_fixture()").update();}reserve(q);assertThat(count("coupon_redemption",owner)).isEqualTo(1);
 }
 @Test void realLifecycleEventsCreateOwnedMaskedNotificationsWithDurableMockReceipts()throws Exception{
  prefs(true,true);var r=reserve(quote("TRAVEL10","2A"));r=action(r,PAY_SUCCESS);r=action(r,TICKET_ISSUE);for(int i=0;i<20;i++){worker.relay();if(!worker.deliver())break;}for(int i=0;i<20&&worker.deliver();i++){}
  var page=notices.list(identity,null);assertThat(page.items()).isNotEmpty();var first=page.items().getFirst();assertThat(first.body()).contains("MOCK").doesNotContain("Traveller","@","card");assertThat(page.items()).allSatisfy(n->{assertThat(n.owner()).isEqualTo(owner);assertThat(n.deliveries()).hasSize(2);assertThat(n.deliveries()).allSatisfy(d->{assertThat(d.state()).isEqualTo("SIMULATED_DELIVERED");assertThat(d.reference()).startsWith("MOCK-NOTIFICATION-");});});
  var read=notices.read(identity,first.id());assertThat(notices.read(identity,first.id()).readAt()).isEqualTo(read.readAt());assertThat(http("PUT","/api/v1/me/notifications/"+first.id()+"/read",issuer.token("bob-"+key(),"bookings:read"),null,null).statusCode()).isEqualTo(404);assertThat(http("GET","/api/v1/me/notifications?after="+first.id(),issuer.token("other-"+key(),"bookings:read"),null,null).statusCode()).isEqualTo(404);
  assertThat(http("GET","/api/v1/me/notifications",token,null,null).statusCode()).isEqualTo(200);
 }
 @Test void dedupOrderingConsentWithdrawalAndHistoryBackfillRemainSafe(){
  prefs(true,false);UUID aggregate=UUID.randomUUID();var newest=signal(aggregate,2,"CONFIRMED");worker.accept(newest);worker.accept(newest);worker.accept(signal(aggregate,1,"CREATED"));assertThat(notices.list(identity,null).items()).hasSize(1);
  var changed=new LifecycleSignal(newest.eventId(),owner,aggregate,aggregate,2,newest.source(),Map.of("status","FAILED"),newest.occurredAt(),newest.route());assertThatThrownBy(()->worker.accept(changed)).isInstanceOf(IllegalStateException.class);
  prefs(false,false);assertThat(worker.deliver()).isTrue();assertThat(notices.list(identity,null).items().getFirst().deliveries().getFirst().state()).isEqualTo("SUPPRESSED");
  prefs(true,true);var history=new LifecycleSignal(UUID.randomUUID(),owner,UUID.randomUUID(),aggregate,1,"flight-extras",Map.of("status","CONFIRMED"),Instant.parse("2020-01-01T00:00:00Z"),newest.route());worker.accept(history);assertThat(notices.list(identity,null).items().getLast().deliveries()).isEmpty();
 }
 @Test void providerReceiptAfterLostAcknowledgementIsRecoveredAndFenced()throws Exception{
  prefs(true,false);worker.accept(signal(UUID.randomUUID(),1,"CONFIRMED"));var claim=store.claim(clock.instant()).orElseThrow();var d=claim.delivery();var log=temporary.resolve("notification-crash.log");var cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
  var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",cp,"com.travbook.support.NotificationProcess",d.id().toString(),d.noticeId().toString(),d.owner().toString(),d.channel().name()).redirectErrorStream(true).redirectOutput(log.toFile());builder.environment().put("DATABASE_URL",postgres.getJdbcUrl());builder.environment().put("DATABASE_USERNAME",postgres.getUsername());builder.environment().put("DATABASE_PASSWORD",postgres.getPassword());var child=builder.start();boolean exited=child.waitFor(40,TimeUnit.SECONDS);if(!exited)child.destroyForcibly();assertThat(exited).isTrue();assertThat(child.exitValue()).as(Files.readString(log)).isEqualTo(35);String reference=gateway.lookup(d).orElseThrow();clock.now=clock.now.plusSeconds(16);
  assertThat(store.complete(claim,claim.delivery().completed(Delivery.State.SIMULATED_DELIVERED,reference,clock.instant()),clock.instant())).isFalse();assertThat(worker.deliver()).isTrue();var notice=notices.list(identity,null).items().getFirst();assertThat(notice.deliveries().getFirst().state()).isEqualTo("SIMULATED_DELIVERED");assertThat(jdbc.sql("select count(*) from notification_provider_receipt where delivery_id=:id").param("id",claim.delivery().id()).query(Long.class).single()).isEqualTo(1);
 }
 @Test void ingressAuditFailureRollsBackAndMalformedSourceIsQuarantined(){
  prefs(true,false);var signal=signal(UUID.randomUUID(),1,"CONFIRMED");jdbc.sql("CREATE FUNCTION reject_notice_fixture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture unavailable'; END; $$").update();jdbc.sql("CREATE TRIGGER reject_notice_fixture BEFORE INSERT ON notification_delivery_event FOR EACH ROW EXECUTE FUNCTION reject_notice_fixture()").update();try{assertThatThrownBy(()->worker.accept(signal)).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(notices.list(identity,null).items()).isEmpty();}finally{jdbc.sql("DROP TRIGGER reject_notice_fixture ON notification_delivery_event").update();jdbc.sql("DROP FUNCTION reject_notice_fixture()").update();}worker.accept(signal);assertThat(notices.list(identity,null).items()).hasSize(1);
  var r=reserve(quote("","2A"));var event=UUID.randomUUID();jdbc.sql("insert into fp_event(id,aggregate_id,version,envelope) values(:event,:id,2,cast(:payload as jsonb))").param("event",event).param("id",r.id()).param("payload",json.writeValueAsString(Map.of("eventId",event,"aggregateId",r.id(),"aggregateVersion",2,"mode","MOCK","payload",Map.of("status","UPDATED")))).update();worker.relay();assertThat(jdbc.sql("select count(*) from lifecycle_rejected where source='flight-extras' and event_id=:event").param("event",event).query(Long.class).single()).isEqualTo(1);
 }
 @Test void unknownDeliveryIsBoundedAndRequiresReviewWithoutFalseSuccess(){
  prefs(true,false);worker.accept(signal(UUID.randomUUID(),1,"CONFIRMED"));var calls=new java.util.concurrent.atomic.AtomicInteger();NotificationGateway broken=new NotificationGateway(){public Optional<String> lookup(Delivery d){return Optional.empty();}public String deliver(Delivery d){calls.incrementAndGet();throw new IllegalStateException("unknown outcome");}};
  var service=new NotificationService(store,broken,customers,organizer,List.of(),clock);for(int i=0;i<3;i++){assertThatThrownBy(service::deliver).isInstanceOf(IllegalStateException.class);clock.now=clock.now.plusSeconds(10);}assertThat(service.deliver()).isFalse();assertThat(calls.get()).isEqualTo(3);var d=notices.list(identity,null).items().getFirst().deliveries().getFirst();assertThat(d.state()).isEqualTo("REVIEW");assertThat(d.reference()).isNull();
 }
}
