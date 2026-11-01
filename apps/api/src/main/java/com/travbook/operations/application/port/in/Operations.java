package com.travbook.operations.application.port.in;
import com.travbook.shared.domain.Recovery.*;
import java.util.*;
public interface Operations {List<Case> list(Kind kind,int page);Case get(Kind kind,UUID id);Action reconcileLegacy(Kind kind,UUID id,long version,String actor,Reason reason,boolean consent,String key,UUID correlation);Action resume(Kind kind,UUID id,long version,String actor,Reason reason,boolean consent,String key,UUID correlation);}
