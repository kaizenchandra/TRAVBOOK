package com.travbook.journeys.application.port.out;
import com.travbook.journeys.domain.model.Care.*;
import java.util.Optional;
public interface JourneyCareEffects {
 record Result(boolean accepted,boolean completed,String reference){}
 void validate(Action action);
 boolean recoveryEvidence(Action action);
 Optional<Result> lookup(Action action);
 Result execute(Action action);
}
