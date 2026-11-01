"use client";
import {useBookingTimeline} from "./hooks";
import {useEffect} from "react";
import {ApiError} from "@/lib/api/client";
import {Loading,ErrorNotice} from "@/components/Feedback";
import {date,time} from "@/lib/format";
import type {components} from "@/lib/api/schema";
const descriptions:Record<components["schemas"]["BookingTimelineEntry"]["eventType"],string>={
  BookingCreated:"Intent saved",BookingValidationStarted:"Offer validation started",BookingAwaitingPayment:"Offer validation completed",
  BookingValidationRetryScheduled:"Validation retry scheduled",BookingManualReviewRequired:"Validation needs review",BookingValidationRejected:"Validation could not complete"
};
export function BookingTimeline({id,version,customerId,onAccessLost}:{id:string;version:number;customerId:string;onAccessLost:(error:Error)=>void}) {
  const timeline=useBookingTimeline(id,version,customerId);
  useEffect(()=>{
    if(!timeline.isFetching&&timeline.error instanceof ApiError&&["UNAUTHENTICATED","FORBIDDEN","NOT_FOUND"].includes(timeline.error.code))onAccessLost(timeline.error);
  },[timeline.error,timeline.isFetching,onAccessLost]);
  const pages=timeline.isError?undefined:timeline.data?.pages,latest=pages?.at(-1);
  const pending=latest&&(latest.deliveryState==="PENDING"||latest.deliveredVersion<version);
  return <section className="booking-timeline" aria-labelledby="timeline-heading"><h3 id="timeline-heading">Intent history</h3>
    <p className="muted">Durably recorded MOCK activity. History may arrive after the current status; it does not confirm travel or payment.</p>
    {timeline.isPending&&<Loading label="Loading intent history…"/>}
    {timeline.isError&&<ErrorNotice error={timeline.error} retry={()=>timeline.refetch()}/>}
    {latest?.deliveryState==="ATTENTION_REQUIRED"?<p className="notice warning" role="status">History delivery needs operational review. Your saved intent and current status are preserved.</p>:
      pending&&<p className="notice warning" role="status">History is catching up. Your current booking status above remains authoritative.</p>}
    {pages&&pages[0].items.length===0&&<p>No history entries have been delivered yet.</p>}
    {pages&&pages[0].items.length>0&&<ol className="intent-history">
      {pages.flatMap(p=>p.items).map(entry=><li key={entry.eventId}><strong>{descriptions[entry.eventType]}</strong>
        <span><time dateTime={entry.occurredAt}>{date(entry.occurredAt)} · {time(entry.occurredAt)} IST</time> · version {entry.version}</span>
        {entry.failureCode&&<span>{entry.failureCode.replaceAll("_"," ")}</span>}
      </li>)}
    </ol>}
    {latest&&<p className="muted">History recorded through version {latest.deliveredVersion}. Automatic checks are bounded and pause in the background.</p>}
    {timeline.hasNextPage&&<button className="button secondary" disabled={timeline.isFetchingNextPage} onClick={()=>timeline.fetchNextPage()}>Load more activity</button>}
    <button className="button secondary" disabled={timeline.isFetching} onClick={()=>timeline.refetch()}>Refresh history</button>
  </section>;
}
