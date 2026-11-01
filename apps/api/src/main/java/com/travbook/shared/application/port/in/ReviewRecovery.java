package com.travbook.shared.application.port.in;
import com.travbook.shared.domain.Recovery.*;
import java.util.*;
public interface ReviewRecovery {
 Kind kind();
 List<Case> list(int page);
 Case get(UUID id);
 Action reconcileLegacy(UUID id,long version,String actor,Reason reason,String key,UUID correlation);
 Action resume(UUID id,long version,String actor,Reason reason,String key,UUID correlation);
}
