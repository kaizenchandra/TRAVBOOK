package com.travbook.journeys.application.port.in;
import com.travbook.journeys.domain.model.*;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.*;
public interface AdvancedJourneys {
 record Replacement(UUID bookingId,long version,com.travbook.shared.domain.TravelMoney originalTotal){}
 record Quotation(Journeys.QuoteDetails details,Care.Terms terms,Replacement replacement){}
 record Page(List<Care.Summary> items,UUID next){}
 Quotation quote(CustomerIdentity identity,Journey.Request request,Care.Plan plan,String key,UUID correlation);
 Quotation quote(CustomerIdentity identity,UUID id);
 Quotation accept(CustomerIdentity identity,UUID id,int version,boolean consent,String key,UUID correlation);
 Care.Summary book(CustomerIdentity identity,UUID quote,List<Journey.Passenger> passengers,String email,Journey.Scenario scenario,String key,UUID correlation);
 Care.Summary get(CustomerIdentity identity,UUID id);
 Page list(CustomerIdentity identity,UUID after);
 Care.Summary pay(CustomerIdentity identity,UUID id,String key,UUID correlation);
 Care.CancellationQuote cancellationQuote(CustomerIdentity identity,UUID id,List<String> ids,String key,UUID correlation);
 Care.Action cancel(CustomerIdentity identity,UUID id,UUID quote,boolean consent,boolean delay,boolean loss,String key,UUID correlation);
 Care.Action balance(CustomerIdentity identity,UUID id,boolean decline,boolean loss,String key,UUID correlation);
 Quotation rescheduleQuote(CustomerIdentity identity,UUID id,Journey.Request request,String key,UUID correlation);
 Care.Action reschedule(CustomerIdentity identity,UUID id,UUID quote,long version,boolean consent,String key,UUID correlation);
 Journey.Document document(CustomerIdentity identity,UUID id);
 Care.Dispatch dispatch(CustomerIdentity identity,UUID id);
 Care.Dispatch advanceDemo(CustomerIdentity identity,UUID id,long version,boolean consent,String key,UUID correlation);
}
