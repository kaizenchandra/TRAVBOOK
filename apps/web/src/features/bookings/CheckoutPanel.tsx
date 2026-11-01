"use client";
import {useEffect,useRef,useState} from "react";
import {useStartCheckout} from "./hooks";
import {ErrorNotice} from "@/components/Feedback";
import {ApiError} from "@/lib/api/client";
import type {components} from "@/lib/api/schema";
type View=components["schemas"]["CheckoutView"];
type Scenario=components["schemas"]["StartCheckout"]["scenario"];
export const checkoutLabel:Record<View["status"],string>={RUNNING:"MOCK checkout in progress",CONFIRMED:"MOCK booking confirmed",FAILED:"MOCK checkout failed",REVIEW:"MOCK checkout needs review"};
export function CheckoutPanel({id,checkout,canStart,refresh,onAccessLost,aftercare=false}:{id:string;checkout:View|null;canStart:boolean;refresh:()=>void;onAccessLost:(error:Error)=>void;aftercare?:boolean}) {
  const start=useStartCheckout(id),key=useRef<string|null>(null);const [consent,setConsent]=useState(false),[scenario,setScenario]=useState<Scenario>("SUCCESS");
  useEffect(()=>{if(start.error instanceof ApiError&&["UNAUTHENTICATED","FORBIDDEN","NOT_FOUND"].includes(start.error.code))onAccessLost(start.error);},[start.error,onAccessLost]);
  async function submit(e:React.FormEvent){e.preventDefault();if(!consent)return;key.current??=crypto.randomUUID();try{await start.mutateAsync({scenario,key:key.current});}catch{/* The same key is retained for an explicit retry; refresh recovers a lost acknowledgement. */}}
  return <section className="booking-timeline" aria-labelledby="checkout-heading"><h3 id="checkout-heading">MOCK checkout</h3>
    <p>No real money, inventory, PNR or ticket is involved. This local demonstration authorizes a simulated payment, reserves with the fictional supplier, then captures.</p>
    {checkout?<>
      <div className={"notice "+(!aftercare&&checkout.status==="CONFIRMED"?"success":"warning")} role="status"><strong>{aftercare?"Historical MOCK checkout completed":checkoutLabel[checkout.status]}</strong>
        <p>{aftercare?"This records the original checkout. Current cancellation and refund status appear below.":checkout.status==="CONFIRMED"?"The MOCK supplier confirmed and the MOCK payment was captured. This reference cannot be used for travel.":checkout.status==="REVIEW"?"An outcome is unresolved. Processing has stopped for operational review; do not start another payment.":checkout.status==="FAILED"?"The workflow could not complete. See the independent supplier and payment states, including any compensation.":"Your request is durable. Unknown outcomes are checked with the provider before another attempt."}</p>
      </div>
      <p>Step: <strong>{checkout.step.replaceAll("_"," ")}</strong> · attempt {checkout.attempts} · version {checkout.version}</p>
      {checkout.failureCode&&<p className="notice warning">{checkout.failureCode.replaceAll("_"," ")}</p>}
      {checkout.paymentReference&&<p className="quote-reference">MOCK payment reference: <code>{checkout.paymentReference}</code></p>}
      {checkout.supplierReference&&<p className="quote-reference">MOCK supplier reference: <code>{checkout.supplierReference}</code></p>}
      <p className="muted">Eligible confirmed MOCK checkouts can generate a private summary. Real tickets and real payment refunds are not available.</p>
    </>:canStart?<form onSubmit={submit}>
      <label className="checkout-scenario" htmlFor="mock-scenario">Demonstration outcome<select id="mock-scenario" value={scenario} disabled={start.isPending||key.current!==null} onChange={e=>setScenario(e.target.value as Scenario)}>
        <option value="SUCCESS">Successful MOCK checkout</option><option value="PAYMENT_DECLINED">Payment authorization declined</option><option value="RESERVATION_REJECTED">Supplier rejects reservation</option><option value="RESERVATION_RESPONSE_LOST">Reservation response lost, then recovered</option><option value="CAPTURE_DECLINED">Capture declined, cancel and void</option><option value="CAPTURE_RESPONSE_LOST">Capture response lost, then recovered</option>
      </select></label>
      <label className="quote-consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/><span>I consent to a MOCK authorization, reservation and capture. No real funds or travel will be purchased.</span></label>
      <button className="button primary" disabled={!consent||start.isPending}>{start.isPending?"Saving MOCK checkout…":"Start MOCK checkout"}</button>
      {start.isError&&<><ErrorNotice error={start.error}/><button type="button" className="button secondary" onClick={refresh}>Recover saved checkout</button></>}
    </form>:<p>Complete offer validation with a valid accepted quotation before starting checkout.</p>}
  </section>;
}
