package com.travbook.simulation.domain.model;

import com.travbook.shared.domain.DomainFailure;
import java.time.Instant;
import java.util.*;

/** Only local simulator work. Its terminal states never assert external delivery or erasure. */
public record DemoOperation(UUID id, UUID owner, Kind kind, Scenario scenario, State status,
                            int attempts, long version, Instant createdAt, Instant updatedAt,
                            Instant nextAttemptAt, String reference, UUID correlation) {
    public enum Kind { EMAIL, SMS, PRIVACY_EXPORT, PRIVACY_ERASURE }
    public enum Scenario { NORMAL, RETRY_ONCE, FAIL }
    public enum State { QUEUED, RUNNING, RETRY, SIMULATED_DELIVERED, SIMULATED_REVIEW_REQUIRED, FAILED }
    public enum Outcome { ACCEPTED, TRANSIENT }
    public DemoOperation {
        Objects.requireNonNull(id); Objects.requireNonNull(owner); Objects.requireNonNull(kind);
        Objects.requireNonNull(scenario); Objects.requireNonNull(status); Objects.requireNonNull(correlation);
        if(attempts<0||attempts>3||version<1)throw new IllegalArgumentException("Invalid simulation state");
    }
    public static DemoOperation queued(UUID owner,Kind kind,Scenario scenario,UUID correlation,Instant now) {
        if(kind==null||scenario==null)throw new DomainFailure(DomainFailure.Code.VALIDATION_FAILED,"Choose a simulation kind and outcome.");
        return new DemoOperation(UUID.randomUUID(),owner,kind,scenario,State.QUEUED,0,1,now,now,now,null,correlation);
    }
    public boolean terminal(){return status==State.SIMULATED_DELIVERED||status==State.SIMULATED_REVIEW_REQUIRED||status==State.FAILED;}
    public String fingerprint(){return owner+":"+kind.name()+":"+scenario.name()+":consented";}
    public DemoOperation claimed(Instant now){
        if(terminal())throw new IllegalStateException("Terminal simulation cannot run");
        return new DemoOperation(id,owner,kind,scenario,State.RUNNING,Math.min(3,attempts+1),version+1,createdAt,now,nextAttemptAt,reference,correlation);
    }
    public DemoOperation complete(Outcome outcome,Instant now){
        if(status!=State.RUNNING)throw new IllegalStateException("Simulation must be claimed");
        State next=outcome==Outcome.ACCEPTED?(kind==Kind.EMAIL||kind==Kind.SMS?State.SIMULATED_DELIVERED:State.SIMULATED_REVIEW_REQUIRED):attempts<3?State.RETRY:State.FAILED;
        return new DemoOperation(id,owner,kind,scenario,next,attempts,version+1,createdAt,now,
            next==State.RETRY?now.plusSeconds(1L<<attempts):now,outcome==Outcome.ACCEPTED?"MOCK-DEMO-"+id:null,correlation);
    }
    public DemoOperation review(Instant now){
        if(status!=State.RUNNING||attempts<3)throw new IllegalStateException("Review requires an exhausted claimed simulation");
        return new DemoOperation(id,owner,kind,scenario,State.SIMULATED_REVIEW_REQUIRED,attempts,version+1,createdAt,now,now,null,correlation);
    }
    public String notice(){return kind==Kind.EMAIL||kind==Kind.SMS?
        "Local test inbox only. No email or SMS is sent to a real recipient.":
        "Simulated privacy request only. No account data is deleted and no complete account export is generated.";}
}
