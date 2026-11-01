package com.travbook.notifications.application.port.out;
import com.travbook.notifications.domain.model.*;
import com.travbook.notifications.application.port.in.Notifications;
import com.travbook.shared.domain.LifecycleSignal;
import java.time.Instant;
import java.util.*;
public interface NotificationStore {
    void accept(LifecycleSignal signal,boolean email,boolean sms,Instant now);
    Notifications.Page list(UUID owner,UUID after);
    Notice read(UUID owner,UUID id,Instant now);
    record Claim(Delivery delivery,UUID token,boolean lookupOnly){}
    Optional<Claim> claim(Instant now);
    boolean complete(Claim claim,Delivery next,Instant now);
}
