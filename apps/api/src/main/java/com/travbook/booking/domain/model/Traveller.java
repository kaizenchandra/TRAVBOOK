package com.travbook.booking.domain.model;
import com.travbook.shared.domain.DomainFailure;
import java.time.*;
import java.text.Normalizer;
import static com.travbook.shared.domain.DomainFailure.Code.VALIDATION_FAILED;
public record Traveller(String givenName,String familyName,LocalDate dateOfBirth) {
    public enum Category { ADULT, CHILD, INFANT }
    public Traveller {
        givenName=name(givenName);familyName=name(familyName);
        if(dateOfBirth==null)throw new DomainFailure(VALIDATION_FAILED,"Every traveller needs a date of birth.");
    }
    private static String name(String value) {
        if(value==null)throw new DomainFailure(VALIDATION_FAILED,"Traveller names are required.");
        String normalized=Normalizer.normalize(value.strip().replaceAll("\\s+"," "),Normalizer.Form.NFC);
        if(normalized.length()<1||normalized.length()>80||!normalized.matches("[\\p{L}\\p{M} '\u2019-]+"))
            throw new DomainFailure(VALIDATION_FAILED,"Use 1–80 letters, spaces, apostrophes or hyphens for each name.");
        return normalized;
    }
    public Category categoryAt(LocalDate departure) {
        int age=Period.between(dateOfBirth,departure).getYears();
        if(dateOfBirth.isAfter(departure)||age>120)throw new DomainFailure(VALIDATION_FAILED,"Check traveller birth dates against the journey dates.");
        return age>=12?Category.ADULT:age>=2?Category.CHILD:Category.INFANT;
    }
}

