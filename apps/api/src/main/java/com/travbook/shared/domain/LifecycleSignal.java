package com.travbook.shared.domain;
import java.time.Instant;
import java.util.*;
/** Minimal published event projection. Never carries travellers, contact details or provider errors. */
public record LifecycleSignal(UUID eventId,UUID owner,UUID aggregateId,UUID bookingId,long version,String source,Map<String,String> states,Instant occurredAt,String route) {
    public LifecycleSignal {Objects.requireNonNull(eventId);Objects.requireNonNull(owner);Objects.requireNonNull(aggregateId);Objects.requireNonNull(bookingId);Objects.requireNonNull(occurredAt);states=Map.copyOf(states);if(version<1||source==null||!source.matches("[a-z-]{1,40}")||states.isEmpty()||!Set.of("status","payment","reservation","refund","ticket").containsAll(states.keySet())||states.values().stream().anyMatch(v->!v.matches("[A-Z0-9_]{1,64}"))||route==null||!route.matches("/(?:bookings/|stays/bookings/|rail-tickets/bookings/|journeys/bookings/|flight-services\\?booking=)[0-9a-f-]{36}"))throw new IllegalArgumentException("Invalid safe lifecycle signal");}
    public String fingerprint(){try{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest((eventId+"|"+owner+"|"+aggregateId+"|"+bookingId+"|"+version+"|"+source+"|"+new TreeMap<>(states)+"|"+occurredAt+"|"+route).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
