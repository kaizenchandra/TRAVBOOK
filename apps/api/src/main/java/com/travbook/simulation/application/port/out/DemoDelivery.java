package com.travbook.simulation.application.port.out;
import com.travbook.simulation.domain.model.DemoOperation;
/** A replaceable local simulator provider. Never an email/SMS or privacy integration. */
public interface DemoDelivery {
    boolean accepted(DemoOperation operation);
    DemoOperation.Outcome deliver(DemoOperation operation);
}
