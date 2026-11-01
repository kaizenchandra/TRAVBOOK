package com.travbook.simulation.application.port.out;
import com.travbook.simulation.domain.model.DemoOperation;
import java.time.Instant;
import java.util.*;
public interface DemoStore {
    record Page(List<DemoOperation> items,UUID next){}
    record Claim(DemoOperation operation,UUID token,boolean lookupOnly){}
    DemoOperation create(DemoOperation candidate,String key);
    DemoOperation get(UUID owner,UUID id);
    Page list(UUID owner,UUID after);
    Optional<Claim> claim(Instant now);
    boolean complete(Claim claim,DemoOperation next);
}
