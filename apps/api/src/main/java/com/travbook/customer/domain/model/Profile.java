package com.travbook.customer.domain.model;

import com.travbook.shared.domain.DomainFailure;
import java.text.Normalizer;
import java.time.Instant;
import java.util.*;
import static com.travbook.shared.domain.DomainFailure.Code.*;

public record Profile(UUID customerId, long version, Fields fields, Instant updatedAt) {
    public Profile {
        Objects.requireNonNull(customerId); Objects.requireNonNull(fields);
        if (version < 0 || (version == 0) != (updatedAt == null)) throw new IllegalArgumentException("Invalid profile version");
    }
    public static Profile empty(UUID owner) { return new Profile(owner, 0, new Fields("", "", ""), null); }
    public Profile change(long expectedVersion, Fields next, Instant now) {
        if (version != expectedVersion) throw new DomainFailure(PROFILE_VERSION_CONFLICT, "Your profile changed. Reload the current profile before saving.");
        return new Profile(customerId, version + 1, next, Objects.requireNonNull(now));
    }
    public List<String> changedFields(Fields next) {
        var changed = new ArrayList<String>();
        if (!fields.displayName().equals(next.displayName())) changed.add("displayName");
        if (!fields.email().equals(next.email())) changed.add("email");
        if (!fields.phone().equals(next.phone())) changed.add("phone");
        return List.copyOf(changed);
    }
    public record Fields(String displayName, String email, String phone) {
        public Fields {
            displayName = clean(displayName, 80); email = clean(email, 254); phone = clean(phone, 16);
            if (!email.isEmpty() && (!email.matches("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9\\-]+(?:\\.[A-Za-z0-9\\-]+)+") || email.indexOf('@') > 64))
                throw new DomainFailure(VALIDATION_FAILED, "Enter a conventional email address or leave it blank.");
            if (!phone.isEmpty() && !phone.matches("\\+[1-9][0-9]{7,14}"))
                throw new DomainFailure(VALIDATION_FAILED, "Use an international phone number with + and 8–15 digits, or leave it blank.");
        }
        private static String clean(String value, int max) {
            if (value == null || value.length() > max || value.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 0x2028 || c == 0x2029))
                throw new DomainFailure(VALIDATION_FAILED, "Check profile field lengths and remove control characters.");
            return Normalizer.normalize(value.strip(), Normalizer.Form.NFC);
        }
        @Override public String toString() { return "Profile.Fields[redacted]"; }
    }
}
