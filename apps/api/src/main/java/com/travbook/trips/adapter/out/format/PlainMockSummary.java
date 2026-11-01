package com.travbook.trips.adapter.out.format;
import com.travbook.trips.application.port.out.DocumentRenderer;
import com.travbook.trips.domain.model.JourneySnapshot;
import java.time.Instant;
import java.time.ZoneId;
public final class PlainMockSummary implements DocumentRenderer {
    public String render(JourneySnapshot s,Instant issuedAt){
        var text=new StringBuilder("MOCK CONFIRMATION SUMMARY — NOT A TICKET OR TAX INVOICE\n\nTravbook development simulation. No real money or travel inventory.\nDo not use this document for travel, reimbursement or tax purposes.\n\n");
        text.append("Booking intent: ").append(s.bookingId()).append("\nCheckout: ").append(s.checkoutId()).append("\nCheckout version: ").append(s.checkoutVersion())
            .append("\nMOCK confirmed at: ").append(s.confirmedAt()).append("\nSummary issued at: ").append(issuedAt)
            .append("\nSupplier status: CONFIRMED (MOCK)\nPayment status: CAPTURED (MOCK)\nSupplier reference: ").append(s.supplierReference()).append("\nPayment reference: ").append(s.paymentReference()).append("\n\nJourney\n");
        for(var l:s.legs())text.append(l.origin()).append(" → ").append(l.destination()).append(" | ").append(l.airline()).append(' ').append(l.flightNumber())
            .append("\nDeparture (local): ").append(l.departure().atZoneSameInstant(ZoneId.of(l.timezone())).toOffsetDateTime())
            .append("\nArrival (local): ").append(l.arrival().atZoneSameInstant(ZoneId.of(l.timezone())).toOffsetDateTime())
            .append("\nJourney timezone: ").append(l.timezone()).append('\n');
        text.append("\nTravellers\n");for(var name:s.travellers())text.append("- ").append(name).append('\n');
        text.append("\nAccepted price (synthetic)\nBase: ").append(s.base().toPlainString()).append(' ').append(s.currency()).append("\nTaxes: ").append(s.taxes().toPlainString()).append(' ').append(s.currency()).append("\nTotal: ").append(s.base().add(s.taxes()).toPlainString()).append(' ').append(s.currency())
            .append("\n\nBaggage fixture: ").append(s.baggage()).append("\nCancellation policy fixture: ").append(s.cancellationPolicy()).append("\n\nThis immutable summary records the state at issue time. Customer cancellation/refund and real travel documents are not enabled.\n");
        return text.toString();
    }
}
