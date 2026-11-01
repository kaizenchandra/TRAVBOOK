package com.travbook.notifications.application.port.in;
import com.travbook.shared.domain.LifecycleSignal;
public interface ProcessNotifications {void accept(LifecycleSignal signal);int relay();boolean deliver();}
