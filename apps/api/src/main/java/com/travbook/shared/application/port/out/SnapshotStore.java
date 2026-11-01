package com.travbook.shared.application.port.out;
import com.travbook.shared.domain.ObservedSnapshot;
import java.util.*;
public interface SnapshotStore<T extends ObservedSnapshot>{void save(T snapshot);Optional<T> find(UUID id);}
