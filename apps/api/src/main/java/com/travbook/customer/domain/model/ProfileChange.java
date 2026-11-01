package com.travbook.customer.domain.model;

import com.travbook.shared.domain.DomainFailure;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.VALIDATION_FAILED;

public record ProfileChange(UUID expectedCustomerId, long expectedVersion, Profile.Fields fields, boolean consent) {
    public ProfileChange {
        Objects.requireNonNull(expectedCustomerId); Objects.requireNonNull(fields);
        if (expectedVersion < 0 || expectedVersion > 9_007_199_254_740_990L || !consent)
            throw new DomainFailure(VALIDATION_FAILED, "A valid current version and consent to store these profile details are required.");
    }
    public String fingerprint() {
        StringBuilder canonical = new StringBuilder().append(expectedCustomerId).append(':').append(expectedVersion).append(":true:");
        for (String value : List.of(fields.displayName(), fields.email(), fields.phone())) canonical.append(value.length()).append(':').append(value);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
