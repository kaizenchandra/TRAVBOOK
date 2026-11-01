package com.travbook.notifications.domain.model;
import java.time.Instant;
import java.util.UUID;
public record Delivery(UUID id,UUID noticeId,UUID owner,Channel channel,State state,int attempts,long version,Instant nextAt,String reference) {
    public enum Channel { EMAIL,SMS }
    public enum State { QUEUED,RUNNING,RETRY,SIMULATED_DELIVERED,SUPPRESSED,FAILED,REVIEW }
    public Delivery claim(Instant now){return new Delivery(id,noticeId,owner,channel,State.RUNNING,Math.min(3,attempts+1),version+1,now,reference);}
    public Delivery completed(State state,String reference,Instant now){if(this.state!=State.RUNNING)throw new IllegalStateException("Delivery was not claimed");if((state==State.SIMULATED_DELIVERED)!=(reference!=null))throw new IllegalStateException("Delivery receipt invariant");return new Delivery(id,noticeId,owner,channel,state,attempts,version+1,state==State.RETRY?now.plusSeconds(1L<<attempts):now,reference);}
    public String fingerprint(){return noticeId+"|"+owner+"|"+channel;}
}
