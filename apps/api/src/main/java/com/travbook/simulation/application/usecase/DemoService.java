package com.travbook.simulation.application.usecase;
import com.travbook.simulation.application.port.in.DemoServices;
import com.travbook.simulation.application.port.out.DemoStore;
import com.travbook.simulation.application.port.out.DemoPositions;
import com.travbook.simulation.domain.model.*;
import com.travbook.customer.application.port.in.*;
import com.travbook.customer.domain.model.Profile;
import com.travbook.journeys.application.port.in.AdvancedJourneys;
import com.travbook.payments.application.port.in.*;
import com.travbook.pricing.application.port.in.Quotations;
import com.travbook.shared.domain.*;
import java.time.Clock;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;
public final class DemoService implements DemoServices {
    private final Customers customers;private final CustomerProfiles profiles;private final Quotations quotes;
    private final AdvancedJourneys journeys;private final PaymentOperations payments;private final PaymentReports reports;
    private final DemoStore store;private final DemoPositions positions;private final Clock clock;
    public DemoService(Customers customers,CustomerProfiles profiles,Quotations quotes,AdvancedJourneys journeys,PaymentOperations payments,PaymentReports reports,DemoStore store,DemoPositions positions,Clock clock){this.customers=customers;this.profiles=profiles;this.quotes=quotes;this.journeys=journeys;this.payments=payments;this.reports=reports;this.store=store;this.positions=positions;this.clock=clock;}
    public List<Capability> catalog(){return List.of(
        new Capability("Flights","Existing owned search, quote, booking and MOCK checkout","/","POST /api/v1/flights/search"),
        new Capability("Hotels","Existing MOCK rooms, guest bookings, payments and aftercare","/hotels","POST /api/v1/hotels/search"),
        new Capability("Trains","Existing MOCK availability, ticket summaries and refunds","/trains","POST /api/v1/trains/search"),
        new Capability("Buses","Existing simulated seats, durable holds and booking","/buses","POST /api/v1/buses/search"),
        new Capability("Holidays","Existing sample packages, quotations and milestones","/holidays","GET /api/v1/holidays/packages"),
        new Capability("Cabs","Existing simulated reservations and dispatch stages","/cabs","POST /api/v1/cabs/search"),
        new Capability("Payments","Use the owned booking checkout; never submit arbitrary charge amounts","/trips","POST /api/v2/bookings/{id}/checkout; POST /api/v2/journey-bookings/{id}/payment"));}
    public DemoOperation create(CustomerIdentity identity,UUID expectedOwner,DemoOperation.Kind kind,DemoOperation.Scenario scenario,boolean consent,String key,UUID correlation){
        if(!consent||key==null||!key.matches("[A-Za-z0-9._:-]{16,128}"))throw new DomainFailure(VALIDATION_FAILED,"Explicit simulation consent and a valid request key are required.");
        var owner=customers.resolve(identity).id();if(!owner.equals(expectedOwner))throw new DomainFailure(PROFILE_IDENTITY_CONFLICT,"Your signed-in account changed. Reload before submitting.");
        return store.create(DemoOperation.queued(owner,kind,scenario,correlation,clock.instant()),key);
    }
    public DemoOperation get(CustomerIdentity identity,UUID id){return store.get(customers.resolve(identity).id(),id);}
    public Page list(CustomerIdentity identity,UUID after){var owner=customers.resolve(identity).id();var p=store.list(owner,after);return new Page(owner,p.items(),p.next());}
    public CouponPreview coupon(CustomerIdentity identity,UUID id,String code){
        var q=quotes.get(identity,id).quote();q.requireValid(clock.instant());var total=q.selection().total().amount();
        return new CouponPreview(q.customerId(),q.id(),DemoCoupon.preview(code,total),TravelMoney.inr(total),q.expiresAt());
    }
    public Gps gps(CustomerIdentity identity,UUID id){
        var d=journeys.dispatch(identity,id);var now=clock.instant();
        var point=positions.atStage(d.stage().name()).orElse(null);
        boolean terminal=point==null||d.stage()==com.travbook.journeys.domain.model.Care.Stage.COMPLETED;
        return new Gps(customers.resolve(identity).id(),d,point==null?null:point.latitude(),point==null?null:point.longitude(),point==null||!now.isBefore(d.staleAt()),terminal,now);
    }
    public Profile privacyPreview(CustomerIdentity identity){return profiles.get(identity);}
    public Report report(CustomerIdentity identity){var owner=customers.resolve(identity).id();return new Report(owner,reports.forCustomer(owner));}
    public PaymentOperations.Current payment(CustomerIdentity identity,UUID id){
        var owner=customers.resolve(identity).id();return payments.current(id).filter(p->p.owner().equals(owner)&&"MOCK".equals(p.mode()))
            .orElseThrow(()->new DomainFailure(NOT_FOUND,"Payment account not found."));
    }
}
