package com.travbook.holidays.application.usecase;
import com.travbook.holidays.application.port.in.ExploreHolidays;
import com.travbook.holidays.application.port.out.*;
import com.travbook.holidays.domain.model.Holiday;
import com.travbook.holidays.domain.model.Holiday.*;
import com.travbook.customer.application.port.in.Customers;
import com.travbook.shared.domain.*;
import java.time.Clock;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
public final class HolidayService implements ExploreHolidays{
 private final HolidaySupplier suppliers;private final HolidayStore store;private final Customers customers;private final Clock clock;
 public HolidayService(HolidaySupplier suppliers,HolidayStore store,Customers customers,Clock clock){this.suppliers=suppliers;this.store=store;this.customers=customers;this.clock=clock;}
 public Page list(Filter f){var inventory=suppliers.catalog(f.destination(),clock.instant());if(inventory.coverage().stream().noneMatch(c->c.status()==SupplierCoverage.Status.AVAILABLE))throw new DomainFailure(DomainFailure.Code.SUPPLIERS_UNAVAILABLE,"All holiday providers are unavailable, not sold out.");var items=inventory.packages().stream().filter(p->f.region()==RegionFilter.ALL||p.region().name().equals(f.region().name())).filter(p->f.theme()==ThemeFilter.ALL||p.theme().name().equals(f.theme().name())).filter(p->p.days()<=f.maxDays()&&p.adultFrom().decimal().compareTo(f.maxPrice())<=0).sorted(f.sort()==Sort.PRICE?Comparator.comparing(p->p.adultFrom().decimal()):Comparator.comparingInt(p->p.days())).toList();return new Page(items,inventory.coverage(),"MOCK",clock.instant(),items.size());}
 public Holiday.Package detail(String id){if(id==null||!id.matches("[a-z0-9-]{1,80}"))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Invalid package identifier.");return suppliers.detail(id,clock.instant());}
 private void key(String key){if(key==null||!key.matches("[A-Za-z0-9_-]{16,100}"))throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Provide a valid Idempotency-Key.");}
 private String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public Quote quote(CustomerIdentity identity,Selection selection,String key,UUID correlation){key(key);var owner=customers.resolve(identity).id();var fingerprint=hash(selection.toString());var prior=store.replay(owner,"QUOTE",key,fingerprint);if(prior.isPresent())return store.quote(owner,prior.get());return store.save(Holiday.quote(owner,detail(selection.packageId()),selection,clock.instant()),key,fingerprint,correlation);}
 public Quote quote(CustomerIdentity identity,UUID id){return store.quote(customers.resolve(identity).id(),id);}
 public Enquiry enquire(CustomerIdentity identity,UUID quote,Contact contact,boolean consent,String key,UUID correlation){key(key);if(!consent)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Consent is required to record this local enquiry.");var owner=customers.resolve(identity).id();return store.saveEnquiry(owner,quote,contact,key,hash(quote+":"+contact),correlation);}
 public UUID enquiryForQuote(CustomerIdentity identity,UUID quote){return store.enquiryForQuote(customers.resolve(identity).id(),quote);}
 public Quote revalidateForBooking(UUID owner,UUID id){var original=store.quote(owner,id);if(original.expired(clock.instant()))throw new DomainFailure(DomainFailure.Code.QUOTE_EXPIRED,"Holiday quotation expired.");var fresh=Holiday.quote(owner,detail(original.selection().packageId()),original.selection(),clock.instant());return new Quote(fresh.id(),owner,1,fresh.travelPackage(),fresh.selection(),fresh.subtotal(),fresh.taxes(),fresh.fees(),fresh.total(),original.createdAt(),original.expiresAt(),"MOCK");}
 public Enquiry enquiry(CustomerIdentity identity,UUID id){return store.enquiry(customers.resolve(identity).id(),id);}
}
