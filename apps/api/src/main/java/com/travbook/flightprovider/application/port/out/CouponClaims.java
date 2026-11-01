package com.travbook.flightprovider.application.port.out;
import com.travbook.flightprovider.domain.model.*;
import java.util.UUID;
/** Local pricing capability participating atomically in quotation/reservation units of work. */
public interface CouponClaims {
    FlightPromotion issue(ProviderQuote quote,String code);
    void redeem(FlightPromotion promotion,UUID reservation,UUID correlation);
}
