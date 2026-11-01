package com.travbook.simulation.application.port.in;
import com.travbook.customer.domain.model.Profile;
import com.travbook.journeys.domain.model.Care;
import com.travbook.payments.application.port.in.PaymentOperations;
import com.travbook.payments.domain.model.FinancialSnapshot;
import com.travbook.shared.domain.*;
import com.travbook.simulation.domain.model.*;
import java.time.Instant;
import java.util.*;
public interface DemoServices {
    record Capability(String name,String description,String route,String api){}
    record Page(UUID customerId,List<DemoOperation> items,UUID next){}
    record CouponPreview(UUID customerId,UUID quoteId,DemoCoupon coupon,TravelMoney originalTotal,Instant expiresAt){}
    record Gps(UUID customerId,Care.Dispatch dispatch,String latitude,String longitude,boolean stale,boolean terminal,Instant asOf){}
    record Report(UUID customerId,FinancialSnapshot snapshot){}
    List<Capability> catalog();
    DemoOperation create(CustomerIdentity identity,UUID expectedOwner,DemoOperation.Kind kind,DemoOperation.Scenario scenario,boolean consent,String key,UUID correlation);
    DemoOperation get(CustomerIdentity identity,UUID id);
    Page list(CustomerIdentity identity,UUID after);
    CouponPreview coupon(CustomerIdentity identity,UUID quoteId,String code);
    Gps gps(CustomerIdentity identity,UUID id);
    Profile privacyPreview(CustomerIdentity identity);
    Report report(CustomerIdentity identity);
    PaymentOperations.Current payment(CustomerIdentity identity,UUID id);
}
