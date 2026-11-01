package com.travbook.booking.application.port.out;
import com.travbook.booking.domain.model.BookingQuote;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.UUID;
public interface BookingQuotePort { BookingQuote load(CustomerIdentity identity,UUID quoteId,int version); }

