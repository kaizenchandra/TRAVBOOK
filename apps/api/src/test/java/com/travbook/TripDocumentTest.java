package com.travbook;
import com.travbook.trips.domain.model.TripDocument;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class TripDocumentTest {
    final String header="MOCK CONFIRMATION SUMMARY — NOT A TICKET OR TAX INVOICE\n";
    TripDocument document(String body,String hash){return new TripDocument(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),1,Instant.now(),body,hash);}
    @Test void tamperedPersistedContentAndUnlabelledBodiesAreRejected(){
        String body=header+"Élodie Example";assertThat(document(body,TripDocument.digest(body)).bytes().length).isGreaterThan(body.length());
        assertThatThrownBy(()->document(body+"changed",TripDocument.digest(body))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->document("Ticket",TripDocument.digest("Ticket"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void byteBudgetUsesUtf8NotCharacterCount(){
        String body=header+"旅".repeat(12000);assertThat(body.length()).isLessThan(32768);
        assertThatThrownBy(()->document(body,TripDocument.digest(body))).isInstanceOf(IllegalArgumentException.class);
    }
}
