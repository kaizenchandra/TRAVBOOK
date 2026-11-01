"use client";
import Link from "next/link";
import { useParams,useSearchParams } from "next/navigation";
import { useEffect,useState } from "react";
import { ArrowLeft,ArrowRight,Plane,ShieldCheck } from "lucide-react";
import { useOffer,useReprice } from "./hooks";
import { date,money,time } from "@/lib/format";
import { ErrorNotice,Loading } from "@/components/Feedback";
export function FlightDetail() {
  const {id}=useParams<{id:string}>();
  const params=useSearchParams(); const back=params.get("returnTo");
  const returnTo=back?.startsWith("/?search=")?back:"/";
  const offer=useOffer(id); const reprice=useReprice(id); const [accepted,setAccepted]=useState(false); const [now,setNow]=useState(Date.now());
  useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer);},[]);
  const fresh=reprice.data&&new Date(reprice.data.validUntil).getTime()>now;
  return <main id="main" className="container detail-page"><Link className="back-link" href={returnTo}><ArrowLeft size={17}/> Back to results</Link>
    {offer.isPending&&<Loading label="Opening flight details…"/>}{offer.isError&&<ErrorNotice error={offer.error} retry={()=>offer.refetch()}/>}
    {offer.data&&<><div className="detail-title"><div className="eyebrow">A CLOSER LOOK AT YOUR JOURNEY</div><h1>{offer.data.airline}</h1><p>Simulated itinerary · {offer.data.cabin.replaceAll("_"," ")} · {offer.data.passengers} traveller(s)</p></div>
    <div className="detail-layout"><div>
      <section className="panel"><h2><Plane size={21}/> Your itinerary</h2>{offer.data.legs.map((leg,i)=><div key={i} className="itinerary-leg"><span className="tag">{i===0?"OUTBOUND":"RETURN"}</span><h3>{leg.origin} <ArrowRight size={18}/> {leg.destination}</h3><div className="flight-line"><div><strong>{time(leg.departure)}</strong><span>{date(leg.departure)}</span></div><div className="flight-duration"><span>{leg.durationMinutes} minutes</span><div className="route-line"><span/><Plane size={15}/></div><small>{leg.stops?"1 simulated stop":"Non-stop"}</small></div><div><strong>{time(leg.arrival)}</strong><span>{date(leg.arrival)}</span></div></div><p className="muted">{leg.flightNumber} · Times in {leg.timezone} (IST). {leg.stops>0&&"Connecting airport and terminal details are not available in this sample."}</p></div>)}</section>
      <section className="panel"><h2>Know before you go</h2><h3>Baggage</h3><p>{offer.data.baggage}</p><h3>Cancellation & changes</h3><p>{offer.data.cancellationPolicy}</p><p className="muted">Sample provider: {offer.data.supplier}. This is a demonstration schedule, not a real airline service.</p></section>
    </div><aside className="panel price-panel"><span className="tag">MOCK FARE</span><h2>Price, without surprises.</h2><dl><div><dt>Base fare</dt><dd>{money((fresh?reprice.data?.offer:offer.data)!.base)}</dd></div><div><dt>Simulated taxes</dt><dd>{money(offer.data.taxes)}</dd></div><div className="total"><dt>Journey total</dt><dd>{money((fresh?reprice.data?.offer:offer.data)!.total)}</dd></div></dl><p>Includes all {offer.data.legs.length===2?"outbound and return ":""}travellers. No payment will be collected.</p>
      <button className="button primary" disabled={reprice.isPending} onClick={()=>{setAccepted(false);reprice.mutate();}}>{reprice.isPending?"Checking current fare…":"Check current fare"}<ArrowRight size={17}/></button>
      {reprice.isError&&<ErrorNotice error={reprice.error}/>}
      <Link className="button secondary quote-link" href={"/quotes/new?offerId="+id}>Get a personal quote</Link>
      {reprice.data&&<div className={"notice "+(fresh?"success":"warning")} role="status">
        {!fresh?<><strong>Fare check expired.</strong><p>Check the current fare again before relying on this price.</p></>:<>
          <strong>{reprice.data.changed?"The fare has changed.":"The fare is unchanged."}</strong>
          {reprice.data.changed&&<p>Previous total {money(reprice.data.previousTotal)}. Current total {money(reprice.data.offer.total)}.</p>}
          {reprice.data.changed&&!accepted&&<button className="button secondary" onClick={()=>setAccepted(true)}>Acknowledge updated fare</button>}
          {accepted&&<p>Updated fare acknowledged for this preview.</p>}
          <p>Checked {time(reprice.data.checkedAt)} IST. Valid until {time(reprice.data.validUntil)} IST. This is not an inventory hold or booking quote.</p>
        </>}
      </div>}
      <p><Link href="/flight-services" className="button secondary">Try seats, meals & check-in</Link></p><p>Seat selection uses the separate Fictional Air Delhi–Mumbai fixture; it does not change this offer.</p>
      <div className="honesty-note"><ShieldCheck size={20}/><span>Personal quotes support durable MOCK booking and payments. No real money or valid travel ticket is issued.</span></div>
    </aside></div></>}
  </main>;
}

