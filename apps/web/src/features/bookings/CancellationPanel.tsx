"use client";
import {useEffect,useRef,useState} from "react";
import {useMutation,useQueryClient} from "@tanstack/react-query";
import {api,unwrap,ApiError} from "@/lib/api/client";
import {csrfHeaders} from "@/features/account/hooks";
import {ErrorNotice} from "@/components/Feedback";
import {money,time} from "@/lib/format";
import type {components} from "@/lib/api/schema";
type View=components["schemas"]["CancellationView"];
type Scenario=components["schemas"]["RequestCancellation"]["scenario"];
export const cancellationLabel:Record<View["cancellationStatus"],string>={PENDING:"MOCK cancellation pending",CANCELLED:"MOCK booking cancelled",REJECTED:"MOCK cancellation not completed",REVIEW:"MOCK cancellation needs review"};
export function CancellationPanel({id,state,refresh,onAccessLost}:{id:string;state?:View|null;refresh:()=>void;onAccessLost:(e:Error)=>void}) {
  const client=useQueryClient(),quoteKey=useRef<string|null>(null),requestKey=useRef<string|null>(null);const [confirmed,setConfirmed]=useState(false),[scenario,setScenario]=useState<Scenario>("SUCCESS");
  const quote=useMutation({mutationFn:async(fresh:boolean)=>{if(fresh||!quoteKey.current)quoteKey.current=crypto.randomUUID();return unwrap(await api.POST("/api/v1/bookings/{id}/cancellation-quotes",{params:{path:{id},header:{"Idempotency-Key":quoteKey.current}},body:{scope:"FULL_BOOKING"},headers:await csrfHeaders()}));},retry:false,onSuccess:()=>{setConfirmed(false);requestKey.current=null;cancel.reset();}});
  const cancel=useMutation({mutationFn:async()=>{if(!quote.data||!confirmed)throw new Error("Accept the cancellation quote first.");requestKey.current??=crypto.randomUUID();return unwrap(await api.POST("/api/v1/bookings/{id}/cancellations",{params:{path:{id},header:{"Idempotency-Key":requestKey.current}},body:{quoteId:quote.data.id,quoteVersion:1,confirmed:true,scenario},headers:await csrfHeaders()}));},retry:false,
    onSuccess:()=>{void client.invalidateQueries({queryKey:["booking"]});void client.invalidateQueries({queryKey:["bookings"]});void client.invalidateQueries({queryKey:["trip-documents"]});}});
  const error=quote.error??cancel.error;
  useEffect(()=>{if(error instanceof ApiError&&["UNAUTHENTICATED","FORBIDDEN","NOT_FOUND"].includes(error.code))onAccessLost(error);},[error,onAccessLost]);
  return <section className="booking-timeline" aria-labelledby="cancellation-heading"><h3 id="cancellation-heading">Cancellation and refund</h3>
    <p>MOCK only. Cancel the whole booking before departure using the policy accepted at purchase. No real money or travel is involved.</p>
    {state?<>
      <div className="notice warning" role="status"><strong>{cancellationLabel[state.cancellationStatus]}</strong><p>{state.cancellationStatus==="CANCELLED"?"The MOCK supplier cancellation is verified. Refund progress is tracked separately below.":state.cancellationStatus==="PENDING"?"The request is saved. A timeout is not proof of cancellation; the worker checks the supplier outcome.":state.cancellationStatus==="REJECTED"?"Cancellation was not completed and no refund was requested. The original reservation remains. Operational assistance is required before another attempt.":"The supplier outcome is unresolved. Processing stopped for operational review; automatic resubmission is unavailable."}</p></div>
      <dl className="booking-states"><div><dt>Refund status</dt><dd>{state.refundStatus.replaceAll("_"," ")}</dd></div><div><dt>Quoted refund</dt><dd>{money(state.refundAmount)}</dd></div><div><dt>Amount retained</dt><dd>{money(state.retainedAmount)}</dd></div></dl>
      {state.refundStatus==="PENDING"&&<p role="status">Refund pending — cancellation does not mean the refund has completed.</p>}
      {state.refundStatus==="SUCCEEDED"&&<p role="status">MOCK refund completed.</p>}
      {state.refundStatus==="NOT_DUE"&&<p>No refund is due under the accepted zero-refund policy.</p>}
      {state.refundStatus==="REVIEW"&&<p className="notice warning">Refund outcome needs operational review. Do not submit another refund.</p>}
      {state.refundReference&&<p className="quote-reference">MOCK refund reference: <code>{state.refundReference}</code></p>}
      {state.failureCode&&<p>{state.failureCode.replaceAll("_"," ")}</p>}
    </>:<>
      <button className="button secondary" disabled={quote.isPending||cancel.isPending} onClick={()=>quote.mutate(!!quote.data)}>{quote.isPending?"Checking cancellation terms…":quote.data?"Refresh cancellation quote":"Request cancellation quote"}</button>
      {quote.data&&!quote.isError&&<div><p>{quote.data.policy}</p><dl className="booking-states"><div><dt>Refund if cancelled</dt><dd>{money(quote.data.refundAmount)}</dd></div><div><dt>Amount retained</dt><dd>{money(quote.data.retainedAmount)}</dd></div></dl><p>Quote expires at {time(quote.data.expiresAt)} IST.</p>
        <label className="checkout-scenario" htmlFor="cancellation-scenario">Cancellation demonstration<select id="cancellation-scenario" value={scenario} disabled={cancel.isPending||requestKey.current!==null} onChange={e=>setScenario(e.target.value as Scenario)}><option value="SUCCESS">Successful cancellation and refund</option><option value="CANCEL_RESPONSE_LOST">Supplier response lost, then recovered</option><option value="REFUND_RESPONSE_LOST">Refund response lost, then recovered</option><option value="REFUND_DELAYED">Delayed refund</option></select></label>
        <label className="quote-consent"><input type="checkbox" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/><span>I accept the displayed refund and retained amounts and confirm cancellation of the whole MOCK booking.</span></label>
        <button className="button primary" disabled={!confirmed||cancel.isPending} onClick={()=>cancel.mutate()}>{cancel.isPending?"Saving cancellation…":"Confirm MOCK cancellation"}</button>
      </div>}
      {error&&<><ErrorNotice error={error}/><button className="button secondary" onClick={refresh}>Recover cancellation status</button></>}
    </>}
  </section>;
}
