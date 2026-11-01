package com.travbook.shared.application.port;
import com.travbook.shared.domain.LifecycleSignal;
import java.util.*;
/** Published producer feed; forwarding acknowledgements are independent of other outbox consumers. */
public interface LifecycleSource {String name();List<LifecycleSignal> pending();void acknowledge(UUID eventId);}
