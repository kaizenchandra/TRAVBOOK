"use client";
import {useEffect,useState} from "react";
import Link from "next/link";
import {useSearchParams} from "next/navigation";
import {Loading,ErrorNotice} from "@/components/Feedback";
import {date,money,time} from "@/lib/format";
import {useHotelRooms,useHotelReprice,type HotelOffer} from "./hooks";
function PlanDetails({offer}:{offer:HotelOffer}){return <><p>{offer.mealPlan==="BREAKFAST"?"Breakfast included":"Room only · No meals"} · Up to {offer.maxAdults} adults + {offer.maxChildren} children</p><p>{offer.refundable?"Refundable before deadline":"Non-refundable at the observed time"}</p>{offer.freeCancellationUntil&&<p>Cancellation deadline: {date(offer.freeCancellationUntil)}, {time(offer.freeCancellationUntil)} IST</p>}<details><summary>Nightly rates, taxes &amp; policy</summary><dl className="hotel-price-lines">{offer.nights.map(n=><div key={n.date}><dt>{date(n.date)}</dt><dd>{money(n.amount)}</dd></div>)}<div><dt>Simulated taxes</dt><dd>{money(offer.taxes)}</dd></div><div><dt>Service fee for this room</dt><dd>{money(offer.fees)}</dd></div></dl><p>{offer.cancellationPolicy}</p><p>MOCK pay-now plan. No payment is collected here. Amenities: {offer.amenities.join(", ")}.</p></details><strong className="hotel-room-total">{money(offer.total)} <small>this room, entire stay</small></strong></>;}
export function HotelDetail({id}:{id:string}){
  const params=useSearchParams(),searchId=params.get("search");const rooms=useHotelRooms(id,searchId),reprice=useHotelReprice();
  const [image,setImage]=useState(0),[accepted,setAccepted]=useState(false),[now,setNow]=useState(Date.now());
  const [selection,setSelection]=useState<Record<string,string>>(()=>Object.fromEntries(params.entries()));
  useEffect(()=>setSelection(Object.fromEntries(params.entries())),[params]);
  useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer);},[]);
  const data=rooms.data;const selected=data?.criteria.rooms.map((_,i)=>selection["room"+i]??"")??[];
  const selectionKey=selected.join(",");useEffect(()=>setAccepted(false),[selectionKey]);
  const checked=reprice.data&&reprice.data.offers.map(o=>o.id).join(",")===selectionKey?reprice.data:undefined;
  const expired=checked?Date.parse(checked.validUntil)<=now:false;
  const complete=!!data&&selected.every((v,i)=>data.offers.some(o=>o.id===v&&o.roomIndex===i));
  function select(index:number,offer:string){const next={...selection,["room"+index]:offer};setSelection(next);const p=new URLSearchParams(next);setAccepted(false);reprice.reset();window.history.replaceState(null,"",`/hotels/${encodeURIComponent(id)}?`+p);}
  async function check(){setAccepted(false);try{await reprice.mutateAsync(selected);}catch{/* Show error; never retain a prior successful total. */}}
  const back=params.get("from");const backUrl=back?.startsWith("/hotels?")?back:"/hotels"+(searchId?"?search="+encodeURIComponent(searchId):"");
  if(!searchId)return <main id="main" className="container detail-page"><h1>Choose dates and rooms first</h1><Link href="/hotels" className="button primary">Search hotels</Link></main>;
  if(rooms.isPending)return <main id="main" className="container"><Loading label="Loading rooms and policies…"/></main>;
  if(rooms.isError)return <main id="main" className="container detail-page"><Link href={backUrl} className="back-link">Back to hotel search</Link><ErrorNotice error={rooms.error} retry={()=>rooms.refetch()}/></main>;
  if(!data)return null;const p=data.property;
  return <main id="main" className="container detail-page hotel-detail"><Link href={backUrl} className="back-link">← Back to hotel results</Link>
    <div className="detail-title"><p className="eyebrow">A PLACE TO MAKE YOURSELF AT HOME</p><h1>{p.name}</h1><p>{p.address} · Near {p.landmark}</p><p>{p.stars} sample stars · {p.rating}/5 sample rating · {p.reviewCount} guest reviews</p><span className="tag">MOCK accommodation · No real inventory</span></div>
    <section className="hotel-gallery" aria-label="Property illustrations"><img src={p.images[image].url} alt={p.images[image].alt} width={800} height={400}/><div>{p.images.map((v,i)=><button key={v.url} aria-label={`View illustration ${i+1}`} aria-pressed={i===image} onClick={()=>setImage(i)}><img src={v.url} alt="" width={160} height={90}/></button>)}</div><p>{p.images[image].attribution}</p></section>
    <div className="hotel-detail-layout"><div><section className="panel"><h2>About this stay</h2><p>{p.description}</p><p>{p.reviewSource}</p><p className="hotel-amenities">{p.amenities.join(" · ")}</p><p>Check-in {p.checkInTime} · Check-out {p.checkOutTime} · {p.timezone}</p><ul>{p.houseRules.map(rule=><li key={rule}>{rule}</li>)}</ul></section>
      <section aria-label="Room comparison"><h2>Choose a plan for each room</h2><p>{date(data.criteria.checkIn)} – {date(data.criteria.checkOut)} · Prices include each room’s entire stay.</p>
        {data.criteria.rooms.map((occupancy,index)=><fieldset className="hotel-room-group" key={index}><legend>Room {index+1} · {occupancy.adults} adult(s){occupancy.childAges.length?` · Children aged ${occupancy.childAges.join(", ")}`:""}</legend>
          {data.offers.filter(o=>o.roomIndex===index).map(o=><article key={o.id} className={"hotel-plan "+(selected[index]===o.id?"selected":"")}><label><input type="radio" name={"room"+index} checked={selected[index]===o.id} onChange={()=>select(index,o.id)}/><span>{o.roomType} · {o.planCode}</span></label><PlanDetails offer={o}/></article>)}
          {data.offers.every(o=>o.roomIndex!==index)&&<p role="status">No eligible rate plans for this occupancy. Change the search.</p>}
        </fieldset>)}
      </section></div>
      <aside className="panel hotel-selection" aria-label="Selected stay"><h2>Your room selection</h2><p>Select one plan per room. The server checks the entire selection for current prices and availability.</p>
        <ul>{selected.map((id,i)=>{const o=data.offers.find(o=>o.id===id&&o.roomIndex===i);return <li key={i}>Room {i+1}: {o?o.roomType+" · "+o.planCode:"Choose a plan"}</li>;})}</ul>
        <button className="button primary" disabled={!complete||reprice.isPending} onClick={check}>{reprice.isPending?"Rechecking rooms…":"Recheck selected rooms"}</button>
        {reprice.isError&&<ErrorNotice error={reprice.error}/>}
        {checked&&!reprice.isPending&&!reprice.isError&&<section aria-label="Revalidated stay" aria-live="polite">
          {expired?<div className="notice warning" role="status">This rate check expired. Recheck the rooms before relying on these prices.</div>:<>
            <h3>{checked.changed?"The price or policy changed":"Current room rates checked"}</h3>{checked.changed&&<p>Previous selection total: {money(checked.previousTotal)}</p>}
            <p className="booking-total">{money(checked.total)}</p><p>All rooms, all nights, simulated taxes and fees included.</p>
            {checked.offers.map(o=><div className="hotel-checked-room" key={o.id}><h3>Room {o.roomIndex+1} · {o.planCode}</h3><PlanDetails offer={o}/></div>)}
            <p>Valid until {time(checked.validUntil)} IST. No rooms are held.</p>
            {checked.changed&&<label className="quote-consent"><input type="checkbox" checked={accepted} onChange={e=>setAccepted(e.target.checked)}/> I have reviewed the updated total and room policies.</label>}
            {(!checked.changed||accepted)&&<p role="status">Rates reviewed for this selection. This is not an accepted quotation or booking.</p>}
          </>}
        </section>}
        {complete&&<Link className="button primary" href={"/stays/quotes/new?offers="+selected.join(",")}>Get an owned hotel quote</Link>}<div className="notice"><strong>Continue with an owned MOCK quotation.</strong><p>Review current prices and policies, sign in, accept your quote and add guests before a simulated booking or payment.</p></div>
      </aside>
    </div>
  </main>;
}
