package com.travbook.pricing.application.port.out;
import com.travbook.pricing.domain.model.FlightSelection;
import java.util.UUID;
public interface RevalidateSelection { FlightSelection revalidate(UUID offerId); }

