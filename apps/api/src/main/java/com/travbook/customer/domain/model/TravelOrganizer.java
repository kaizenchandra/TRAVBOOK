package com.travbook.customer.domain.model;
import com.travbook.shared.domain.DomainFailure;
import java.time.*;
import java.text.Normalizer;
import java.util.*;
public record TravelOrganizer(UUID customerId,long version,Fields fields,Instant updatedAt) {
    public record Traveller(UUID id,String name,LocalDate dateOfBirth){public Traveller{name=text(name);Objects.requireNonNull(id);Objects.requireNonNull(dateOfBirth);}}
    public record Bookmark(UUID id,String label,String path){public Bookmark{Objects.requireNonNull(id);label=text(label);if(!Set.of("/","/hotels","/trains","/buses","/holidays","/cabs","/flight-services").contains(path))throw invalid("Use a supported internal travel category.");}}
    public record Criteria(String origin,String destination,LocalDate departureDate,LocalDate returnDate,int adults,int children,int infants,String cabin){
        public Criteria{if(origin==null||destination==null||!origin.matches("[A-Z]{3}")||!destination.matches("[A-Z]{3}")||origin.equals(destination)||departureDate==null||returnDate!=null&&returnDate.isBefore(departureDate)||adults<1||children<0||infants<0||adults+children>9||infants>adults||!Set.of("ECONOMY","PREMIUM_ECONOMY","BUSINESS","FIRST").contains(cabin))throw invalid("Check the saved flight search criteria.");}
    }
    public record Search(UUID id,String label,Criteria criteria){public Search{Objects.requireNonNull(id);label=text(label);Objects.requireNonNull(criteria);}}
    public record Preferences(boolean email,boolean sms,boolean marketing){}
    public record Fields(List<Traveller> travellers,List<Bookmark> bookmarks,List<Search> searches,Preferences preferences){
        public Fields{travellers=bounded(travellers);bookmarks=bounded(bookmarks);searches=bounded(searches);Objects.requireNonNull(preferences);unique(travellers.stream().map(t->t.id()).toList());unique(bookmarks.stream().map(b->b.id()).toList());unique(searches.stream().map(s->s.id()).toList());}
        public void validate(Instant now){var today=now.atZone(ZoneOffset.UTC).toLocalDate();for(var t:travellers)if(t.dateOfBirth().isAfter(today)||t.dateOfBirth().isBefore(today.minusYears(120)))throw invalid("Traveller birth dates must be within the past 120 years.");}
        @Override public String toString(){return "OrganizerFields[redacted]";}
    }
    public static TravelOrganizer empty(UUID owner){return new TravelOrganizer(owner,0,new Fields(List.of(),List.of(),List.of(),new Preferences(false,false,false)),null);}
    public TravelOrganizer change(long expected,Fields next,Instant now){if(expected!=version)throw new DomainFailure(DomainFailure.Code.PROFILE_VERSION_CONFLICT,"Your saved travel details changed. Reload before saving.");next.validate(now);return new TravelOrganizer(customerId,version+1,next,now);}
    private static String text(String value){if(value==null||value.isBlank()||value.length()>80||value.codePoints().anyMatch(Character::isISOControl))throw invalid("Labels and names require 1–80 characters without controls.");return Normalizer.normalize(value.strip(),Normalizer.Form.NFC);}
    private static <T>List<T> bounded(List<T> values){if(values==null||values.size()>20||values.stream().anyMatch(Objects::isNull))throw invalid("Keep at most 20 entries in each collection.");return List.copyOf(values);}
    private static void unique(List<UUID> ids){if(new HashSet<>(ids).size()!=ids.size())throw invalid("Entry identifiers must be unique.");}
    private static DomainFailure invalid(String text){return new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,text);}
}
