package com.travbook.search.domain.model;
import java.time.*;
import java.util.*;
public record SearchSnapshot(UUID id, FlightQuery criteria, List<FlightOffer> offers,
                             List<Coverage> coverage, Instant createdAt, Instant expiresAt) {
    public SearchSnapshot { offers = List.copyOf(offers); coverage = List.copyOf(coverage); }
    public record Coverage(String supplier, Status status) {
        public enum Status { AVAILABLE, UNAVAILABLE }
    }
    public boolean partial() { return coverage.stream().anyMatch(c -> c.status() == Coverage.Status.UNAVAILABLE); }
}

