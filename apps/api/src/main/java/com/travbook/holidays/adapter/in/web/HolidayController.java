package com.travbook.holidays.adapter.in.web;
import com.travbook.holidays.application.port.in.ExploreHolidays;
import com.travbook.holidays.domain.model.Holiday;
import com.travbook.holidays.domain.model.Holiday.*;
import com.travbook.platform.security.AuthenticatedIdentity;
import com.travbook.shared.domain.TravelMoney;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
@RestController public class HolidayController{
 private final ExploreHolidays holidays;private final Clock clock;public HolidayController(ExploreHolidays holidays,Clock clock){this.holidays=holidays;this.clock=clock;}
 public record PackageView(String id,String supplier,String title,String destination,Region region,Theme theme,int nights,int days,String image,String summary,List<String> highlights,List<Day> itinerary,List<String> inclusions,List<String> exclusions,List<LocalDate> departures,TravelMoney adultFrom,boolean customizable,Fulfilment fulfilment,String cancellationPolicy,String paymentPolicy,String eligibility,String timezone,String mode){static PackageView of(Holiday.Package p){return new PackageView(p.id(),p.supplier(),p.title(),p.destination(),p.region(),p.theme(),p.nights(),p.days(),p.image(),p.summary(),p.highlights(),p.itinerary(),p.inclusions(),p.exclusions(),p.departures(),p.adultFrom(),p.customizable(),p.fulfilment(),p.cancellationPolicy(),p.paymentPolicy(),p.eligibility(),p.timezone(),"MOCK");}}
 public record PageView(List<PackageView> items,List<com.travbook.shared.domain.SupplierCoverage> coverage,String mode,Instant observedAt,int total){static PageView of(Page p){return new PageView(p.items().stream().map(v->PackageView.of(v)).toList(),p.coverage(),"MOCK",p.observedAt(),p.total());}}
 public record QuoteRequest(@NotBlank @Pattern(regexp="[a-z0-9-]{1,80}") String packageId,@NotNull LocalDate departure,@Min(1) @Max(6) int adults,@NotNull @Size(max=4) List<@NotNull @Min(0) @Max(11) Integer> childAges,@NotNull Upgrade upgrade,boolean extraActivity){Selection domain(){return new Selection(packageId,departure,adults,childAges,upgrade,extraActivity);}}
 public record EnquiryRequest(@NotNull UUID quoteId,@NotBlank @Size(max=60) String contactName,@NotBlank @Email @Size(max=160) String email,@NotNull @Size(max=500) String notes,@AssertTrue boolean consent){}
 public record QuoteView(UUID id,int version,@JsonProperty("package") PackageView travelPackage,Selection selection,TravelMoney subtotal,TravelMoney taxes,TravelMoney fees,TravelMoney total,Instant createdAt,Instant expiresAt,boolean expired,String mode,UUID enquiryId){static QuoteView of(Quote q,Instant now,UUID enquiry){return new QuoteView(q.id(),q.version(),PackageView.of(q.travelPackage()),q.selection(),q.subtotal(),q.taxes(),q.fees(),q.total(),q.createdAt(),q.expiresAt(),q.expired(now),"MOCK",enquiry);}}
 public record EnquiryView(UUID id,QuoteView quote,String contactName,String email,String notes,String status,Instant createdAt,String mode){static EnquiryView of(Enquiry e,Instant now){return new EnquiryView(e.id(),QuoteView.of(e.quote(),now,e.id()),e.contact().contactName(),e.contact().email(),e.contact().notes(),e.status(),e.createdAt(),"MOCK");}}
 @GetMapping("/api/v1/holidays/packages") public PageView list(@RequestParam(defaultValue="") String destination,@RequestParam(defaultValue="ALL") RegionFilter region,@RequestParam(defaultValue="ALL") ThemeFilter theme,@RequestParam(defaultValue="99999999") BigDecimal maxPrice,@RequestParam(defaultValue="30") int maxDays,@RequestParam(defaultValue="PRICE") Sort sort){return PageView.of(holidays.list(new Filter(destination,region,theme,maxPrice,maxDays,sort)));}
 @GetMapping("/api/v1/holidays/packages/{id}") public PackageView detail(@PathVariable String id){return PackageView.of(holidays.detail(id));}
 @PostMapping("/api/v1/holidays/quotes") @ResponseStatus(HttpStatus.CREATED) public QuoteView quote(Authentication a,@Valid @RequestBody QuoteRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){var identity=AuthenticatedIdentity.from(a);var q=holidays.quote(identity,r.domain(),key,correlation(h));return QuoteView.of(q,clock.instant(),holidays.enquiryForQuote(identity,q.id()));}
 @GetMapping("/api/v1/holidays/quotes/{id}") public QuoteView quote(Authentication a,@PathVariable UUID id){var identity=AuthenticatedIdentity.from(a);return QuoteView.of(holidays.quote(identity,id),clock.instant(),holidays.enquiryForQuote(identity,id));}
 @PostMapping("/api/v1/holidays/enquiries") @ResponseStatus(HttpStatus.CREATED) public EnquiryView enquiry(Authentication a,@Valid @RequestBody EnquiryRequest r,@RequestHeader("Idempotency-Key") String key,HttpServletRequest h){return EnquiryView.of(holidays.enquire(AuthenticatedIdentity.from(a),r.quoteId(),new Contact(r.contactName(),r.email(),r.notes()),r.consent(),key,correlation(h)),clock.instant());}
 @GetMapping("/api/v1/holidays/enquiries/{id}") public EnquiryView enquiry(Authentication a,@PathVariable UUID id){return EnquiryView.of(holidays.enquiry(AuthenticatedIdentity.from(a),id),clock.instant());}
 private UUID correlation(HttpServletRequest r){return UUID.fromString((String)r.getAttribute("correlationId"));}
}
