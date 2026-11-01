package com.travbook;
import com.travbook.stays.domain.model.Stay.Guest;
import com.travbook.shared.domain.DomainFailure;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;
class StayGuestTest {
 @Test void unicodeMarksAndCanonicalNamesAreSupported(){var dob=LocalDate.parse("1990-01-01");assertThat(new Guest(0,"अनन्या","शर्मा",dob).givenName()).isEqualTo("अनन्या");assertThat(new Guest(0," Jose\u0301 ","D’Costa",dob)).isEqualTo(new Guest(0,"José","D’Costa",dob));}
 @Test void punctuationOnlyAndControlMarkupAreRejected(){var dob=LocalDate.parse("1990-01-01");assertThatThrownBy(()->new Guest(0,"...","Example",dob)).isInstanceOf(DomainFailure.class);assertThatThrownBy(()->new Guest(0,"<script>","Example",dob)).isInstanceOf(DomainFailure.class);}
}
