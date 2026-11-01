package com.travbook.notifications.application.port.out;
import com.travbook.notifications.domain.model.Delivery;
import java.util.Optional;
public interface NotificationGateway {Optional<String> lookup(Delivery delivery);String deliver(Delivery delivery);}
