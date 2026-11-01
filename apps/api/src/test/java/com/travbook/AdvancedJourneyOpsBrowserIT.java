package com.travbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.travbook.journeys.domain.model.Care;
import java.nio.file.*;
import java.util.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.*;
class AdvancedJourneyOpsBrowserIT extends AdvancedJourneyIntegrationTest {
 @TempDir Path temp;
 @Test void desktopAndMobileRecoverPartialCancellation()throws Exception{int offset=87;for(String viewport:List.of("desktop","mobile")){var b=confirmed(advancedBook(busParty(++offset),Care.Plan.FULL));cancel(b,List.of("L2"),false);var claim=careStore.claim().orElseThrow();careEffects.execute(claim.action());assertThat(careStore.complete(claim,claim.action().review(clock.instant()))).isTrue();var log=temp.resolve("care-"+viewport+".log");var pb=new ProcessBuilder("node","../../scripts/verify-operations-browser.mjs").redirectErrorStream(true).redirectOutput(log.toFile());pb.environment().put("OPS_TEST_API","http://127.0.0.1:"+port);pb.environment().put("OPS_TEST_OPERATOR",issuer.token("care-operator","",Map.of("travbook_roles",List.of("operations_reconcile"),"amr",List.of("mfa"),"auth_time",Instant.now().getEpochSecond())));pb.environment().put("OPS_TEST_CUSTOMER",token);pb.environment().put("OPS_TEST_BOOKING",b.booking().id().toString());pb.environment().put("OPS_TEST_VIEWPORT",viewport);pb.environment().put("OPS_TEST_KIND","BUS");var child=pb.start();boolean done=child.waitFor(45,TimeUnit.SECONDS);if(!done)child.destroyForcibly();assertThat(done).isTrue();assertThat(child.exitValue()).as(Files.readString(log)).isZero();var result=finishCare(b.booking().id());assertThat(result.activeComponentIds()).containsExactly("L1");assertThat(result.funding().refunded().decimal()).isEqualByComparingTo("700");assertThat(jdbc.sql("select count(*) from journey_supplier_care_receipt where booking_id=:id").param("id",b.booking().id()).query(Integer.class).single()).isEqualTo(1);}}
}
