package com.travbook;
import com.travbook.customer.application.port.in.*;
import com.travbook.customer.domain.model.TravelOrganizer;
import com.travbook.shared.domain.*;
import com.travbook.support.TestIssuer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import java.net.*;import java.net.http.*;import java.time.*;import java.util.*;import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
@Testcontainers
class OrganizerIntegrationTest {
 static final TestIssuer issuer=new TestIssuer();@Container static PostgreSQLContainer postgres=new PostgreSQLContainer("postgres:17.6-alpine");
 @DynamicPropertySource static void config(DynamicPropertyRegistry r){r.add("spring.datasource.url",postgres::getJdbcUrl);r.add("spring.datasource.username",postgres::getUsername);r.add("spring.datasource.password",postgres::getPassword);r.add("travbook.oidc-issuer",issuer::url);r.add("travbook.local-identity",()->true);}
 @Autowired CustomerOrganizer organizer;@Autowired Customers customers;@Autowired JdbcClient jdbc;@Autowired ObjectMapper json;@LocalServerPort int port;
 CustomerIdentity identity;UUID owner;String token;
 @BeforeEach void init()throws Exception{identity=new CustomerIdentity(issuer.url(),UUID.randomUUID().toString());owner=customers.resolve(identity).id();token=issuer.token(identity.subject(),"");}
 @AfterAll static void close(){issuer.close();}
 String key(){return UUID.randomUUID().toString();}
 TravelOrganizer.Fields fields(){return new TravelOrganizer.Fields(List.of(new TravelOrganizer.Traveller(UUID.randomUUID(),"Private Traveller",LocalDate.of(1990,1,1))),List.of(new TravelOrganizer.Bookmark(UUID.randomUUID(),"Mountain holiday","/holidays")),List.of(new TravelOrganizer.Search(UUID.randomUUID(),"Next journey",new TravelOrganizer.Criteria("DEL","BOM",LocalDate.now().plusDays(2),null,1,0,0,"ECONOMY"))),new TravelOrganizer.Preferences(true,false,false));}
 CustomerOrganizer.Saved save(long version,TravelOrganizer.Fields fields,String key){return organizer.save(identity,owner,version,fields,true,key,UUID.randomUUID());}
 HttpResponse<String> request(String method,String token,Object body)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/me/travel-organizer")).timeout(Duration.ofSeconds(10));if(token!=null)b.header("Authorization","Bearer "+token);if(body==null)b.GET();else b.header("Content-Type","application/json").header("Idempotency-Key",key()).method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));return HttpClient.newHttpClient().send(b.build(),HttpResponse.BodyHandlers.ofString());}
 @Test void actualHttpPersistsOwnedFieldsAndRejectsCrossAccountWrites()throws Exception{
  assertThat(request("GET",null,null).statusCode()).isEqualTo(401);assertThat(organizer.get(identity).version()).isZero();var fields=fields();
  var result=request("PUT",token,Map.of("expectedCustomerId",owner,"expectedVersion",0,"fields",fields,"consent",true));assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
  var saved=organizer.get(identity);assertThat(saved.fields()).isEqualTo(fields);assertThat(saved.version()).isEqualTo(1);
  var bob=new CustomerIdentity(issuer.url(),key());assertThat(organizer.get(bob).fields().travellers()).isEmpty();
  assertThat(request("PUT",issuer.token(bob.subject(),""),Map.of("expectedCustomerId",owner,"expectedVersion",1,"fields",fields,"consent",true)).statusCode()).isEqualTo(409);
  assertThat(request("PUT",token,Map.of("expectedCustomerId",owner,"expectedVersion",1,"fields",fields,"consent",false)).statusCode()).isEqualTo(422);
  assertThat(request("GET",token,null).headers().firstValue("Cache-Control").orElse("")).contains("no-store");
 }
 @Test void concurrentReplacementIsVersionGuardedAndLostResponseReplaysWithoutHistoricalPii()throws Exception{
  var fields=fields();String key=key();try(var pool=Executors.newFixedThreadPool(3)){var tasks=List.<Callable<CustomerOrganizer.Saved>>of(()->save(0,fields,key),()->save(0,fields,key),()->save(0,fields,key));for(var r:pool.invokeAll(tasks))assertThat(r.get().appliedVersion()).isEqualTo(1);}
  var cleared=TravelOrganizer.empty(owner).fields();save(1,cleared,key());var replay=save(0,fields,key);assertThat(replay.replayed()).isTrue();assertThat(replay.appliedVersion()).isEqualTo(1);assertThat(replay.organizer().fields().travellers()).isEmpty();
  assertThatThrownBy(()->save(1,fields,key())).isInstanceOf(DomainFailure.class);assertThatThrownBy(()->save(0,cleared,key)).isInstanceOf(DomainFailure.class);
  assertThat(jdbc.sql("select row_to_json(c)::text from organizer_command c where customer_id=:owner").param("owner",owner).query(String.class).list()).allSatisfy(row->assertThat(row).doesNotContain("Private Traveller","1990-01-01"));
  assertThat(jdbc.sql("select payload::text from customer_organizer where customer_id=:owner").param("owner",owner).query(String.class).single()).doesNotContain("Private Traveller","1990-01-01");
 }
 @Test void auditFailureRollsBackPersonalDataAndVersion(){
  jdbc.sql("CREATE FUNCTION fail_organizer_fixture() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'fixture failure'; END; $$").update();jdbc.sql("CREATE TRIGGER fail_organizer_fixture BEFORE INSERT ON organizer_command FOR EACH ROW EXECUTE FUNCTION fail_organizer_fixture()").update();
  try{assertThatThrownBy(()->save(0,fields(),key())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThat(organizer.get(identity).version()).isZero();}finally{jdbc.sql("DROP TRIGGER fail_organizer_fixture ON organizer_command").update();jdbc.sql("DROP FUNCTION fail_organizer_fixture()").update();}
  assertThat(save(0,fields(),key()).appliedVersion()).isEqualTo(1);
 }
 @Test void validatesBoundsDatesSearchesAndSafeBookmarks(){
  assertThatThrownBy(()->new TravelOrganizer.Bookmark(UUID.randomUUID(),"Unsafe","https://outside.example")).isInstanceOf(DomainFailure.class);
  var many=new ArrayList<TravelOrganizer.Traveller>();for(int i=0;i<21;i++)many.add(new TravelOrganizer.Traveller(UUID.randomUUID(),"Example",LocalDate.of(1990,1,1)));
  assertThatThrownBy(()->new TravelOrganizer.Fields(many,List.of(),List.of(),new TravelOrganizer.Preferences(false,false,false))).isInstanceOf(DomainFailure.class);
  var future=new TravelOrganizer.Fields(List.of(new TravelOrganizer.Traveller(UUID.randomUUID(),"Future",LocalDate.now().plusDays(1))),List.of(),List.of(),new TravelOrganizer.Preferences(false,false,false));assertThatThrownBy(()->save(0,future,key())).isInstanceOf(DomainFailure.class);
  assertThatThrownBy(()->new TravelOrganizer.Criteria("DEL","DEL",LocalDate.now(),null,1,0,0,"ECONOMY")).isInstanceOf(DomainFailure.class);
 }
}
