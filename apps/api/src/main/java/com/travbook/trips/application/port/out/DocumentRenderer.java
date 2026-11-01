package com.travbook.trips.application.port.out;
import com.travbook.trips.domain.model.JourneySnapshot;
import java.time.Instant;
public interface DocumentRenderer { String render(JourneySnapshot snapshot,Instant issuedAt); }
