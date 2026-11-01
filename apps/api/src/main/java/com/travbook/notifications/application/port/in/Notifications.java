package com.travbook.notifications.application.port.in;
import com.travbook.notifications.domain.model.Notice;
import com.travbook.shared.domain.CustomerIdentity;
import java.util.*;
public interface Notifications {
    record Page(UUID customerId,List<Notice> items,UUID next){}
    Page list(CustomerIdentity identity,UUID after);
    Notice read(CustomerIdentity identity,UUID id);
}
