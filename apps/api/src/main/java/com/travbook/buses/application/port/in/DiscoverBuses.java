package com.travbook.buses.application.port.in;
import com.travbook.buses.domain.model.Bus.*;
import java.util.*;
public interface DiscoverBuses {Page search(Criteria criteria);Page page(UUID id,Filter filter);Detail trip(UUID search,UUID trip,boolean fresh);FareCheck reprice(UUID search,UUID trip,List<String> seats,String boarding,String dropping);}
