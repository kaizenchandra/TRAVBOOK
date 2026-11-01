package com.travbook.trips.domain.model;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
public record TripDocument(UUID id,UUID customerId,UUID bookingId,UUID checkoutId,long checkoutVersion,Instant createdAt,String body,String sha256) {
    public static final String KIND="MOCK_CONFIRMATION_SUMMARY",MEDIA_TYPE="text/plain; charset=UTF-8";
    public TripDocument {
        if(id==null||customerId==null||bookingId==null||checkoutId==null||checkoutVersion<1||createdAt==null||body==null||body.indexOf('\0')>=0||
            body.getBytes(StandardCharsets.UTF_8).length>32768||!body.startsWith("MOCK CONFIRMATION SUMMARY — NOT A TICKET OR TAX INVOICE")||!digest(body).equals(sha256))throw new IllegalArgumentException("Invalid document content or integrity");
    }
    public byte[] bytes(){return body.getBytes(StandardCharsets.UTF_8);}
    public static String digest(String body){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}}
}
