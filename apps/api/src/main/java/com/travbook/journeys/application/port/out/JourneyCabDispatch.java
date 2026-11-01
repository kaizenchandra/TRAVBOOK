package com.travbook.journeys.application.port.out;
import com.travbook.journeys.domain.model.Care.Dispatch;
import java.util.UUID;
public interface JourneyCabDispatch {Dispatch get(UUID booking);Dispatch advance(UUID owner,UUID booking,long version,String key);}
