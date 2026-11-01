"use client";
import { useEffect, useState } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ArrowRight, ArrowLeftRight, Plane, TrainFront, BusFront, Hotel, Palmtree, Car, Search, SlidersHorizontal, ChevronRight } from "lucide-react";
import { useFlightSearch, useSearchPage, type Filters } from "./hooks";
import { type Offer, type SearchRequest } from "@/lib/api/client";
import { date, money, time, travelDate } from "@/lib/format";
import { LocationInput } from "@/components/LocationInput";
import { Loading, ErrorNotice } from "@/components/Feedback";
import {loadFlightDraft,saveFlightDraft} from './draft';
const services=[{label:"Flights",icon:Plane},{label:"Hotels",icon:Hotel},{label:"Trains",icon:TrainFront},{label:"Buses",icon:BusFront},{label:"Holidays",icon:Palmtree},{label:"Cabs",icon:Car}];
const initial=():SearchRequest=>({origin:"DEL",destination:"BOM",departureDate:travelDate(1),returnDate:null,adults:1,children:0,infants:0,cabin:"ECONOMY"});
function filtersFrom(params:Pick<URLSearchParams,"get">):Filters {
  return {page:Math.max(0,Number(params.get("page"))||0),size:5,nonstop:params.get("nonstop")==="true",
    sort:(["PRICE","DURATION","DEPARTURE"].includes(params.get("sort")??"")?params.get("sort"):"PRICE") as Filters["sort"],airline:params.get("airline")??""};
}
export function FlightSearch() {
  const router=useRouter(); const params=useSearchParams(); const searchId=params.get("search");
  const [form,setForm]=useState<SearchRequest>(initial); const [validation,setValidation]=useState("");
  const [draftReady,setDraftReady]=useState(false);
  useEffect(()=>{if(!searchId){const draft=loadFlightDraft();if(draft)setForm(draft);}setDraftReady(true);},[]);
  useEffect(()=>{if(draftReady)saveFlightDraft(form);},[form,draftReady]);
  const mutation=useFlightSearch();
  const [filters,setFilters]=useState<Filters>(()=>filtersFrom(params));
  useEffect(()=>setFilters(filtersFrom(params)),[params]);
  const results=useSearchPage(searchId,filters);
  useEffect(()=>{if(results.data)setForm(results.data.criteria);},[results.data?.id]); // restore canonical search after refresh/back
  const update=<K extends keyof SearchRequest>(key:K,value:SearchRequest[K])=>setForm(f=>({...f,[key]:value}));
  function changeFilter(key:string,value:string) {
    const next=new URLSearchParams(params); next.set(key,value); if(key!=="page")next.delete("page");
    setFilters(filtersFrom(next));
    router.push("/?"+next.toString(),{scroll:false});
  }
  async function submit(e:React.FormEvent) {
    e.preventDefault();setValidation("");
    if(!form.origin||!form.destination){setValidation("Select an airport from each suggestion list.");return;}
    if(form.origin===form.destination){setValidation("Choose two different airports.");return;}
    if(form.returnDate&&form.returnDate<form.departureDate){setValidation("Return must be on or after departure.");return;}
    if(form.adults+form.children>9||form.infants>form.adults){setValidation("Choose up to 9 seated travellers and at most one infant per adult.");return;}
    try {const data=await mutation.mutateAsync(form);router.push("/?search="+data.id,{scroll:false});}catch{/* mutation error is displayed */}
  }
  const resultUrl="/?"+params.toString();
  return <main id="main">
    <section className={"hero "+(searchId?"compact":"")}><div className="container hero-content">
      <div className="eyebrow"><span/> A LITTLE CURIOSITY. A NEW HORIZON.</div>
      <h1>Book flights. Find your next destination.</h1>
      <p>Compare domestic flights, review the full fare and manage your journey in one place.</p><p><Link className="button secondary" href="/flight-services">Choose flight seats & meals</Link></p>
      <div className="landscape" aria-hidden="true"><span className="sun"/><span className="mountain far"/><span className="mountain near"/><span className="flight-path"/><Plane className="hero-plane" size={48}/><span className="landscape-label">MORE POSSIBILITY. LESS GUESSWORK.</span></div>
    </div></section>
    <section className="container search-section" aria-label="Find flights">
      <div className="search-card">
        <div className="service-nav" aria-label="Travel services">{services.map(({label,icon:Icon},i)=>(i>0)?<Link key={label} href={["/","/hotels","/trains","/buses","/holidays","/cabs"][i]} className="service"><Icon size={23}/><strong>{label}</strong><small>{i===1?"Explore sample stays":i===2?"Explore sample trains":"Explore samples"}</small></Link>:<div key={label} className={"service "+(i===0?"active":"unavailable")}><Icon size={23}/><strong>{label}</strong>{i>2&&<small>Not available yet</small>}</div>)}</div>
        <form onSubmit={submit}>
          <div className="search-toolbar"><fieldset className="trip-type"><legend className="sr-only">Trip type</legend>
            <label><input type="radio" name="trip" checked={!form.returnDate} onChange={()=>update("returnDate",null)}/> One way</label>
            <label><input type="radio" name="trip" checked={!!form.returnDate} onChange={()=>update("returnDate",form.departureDate)}/> Round trip</label>
          </fieldset><span>Sample domestic routes · INR</span></div>
          <div className="search-fields">
            <LocationInput label="From" value={form.origin} onChange={v=>update("origin",v)}/>
            <button className="swap-button" aria-label="Swap origin and destination" type="button" onClick={()=>setForm(f=>({...f,origin:f.destination,destination:f.origin}))}><ArrowLeftRight size={18}/></button>
            <LocationInput label="To" value={form.destination} onChange={v=>update("destination",v)}/>
            <div className="field"><label htmlFor="departure">Departure</label><input id="departure" type="date" min={travelDate()} max={travelDate(330)} required value={form.departureDate} onChange={e=>update("departureDate",e.target.value)}/></div>
            {form.returnDate!==null&&<div className="field"><label htmlFor="return">Return</label><input id="return" type="date" min={form.departureDate} max={travelDate(330)} required value={form.returnDate} onChange={e=>update("returnDate",e.target.value)}/></div>}
            <div className="field"><label htmlFor="cabin">Cabin</label><select id="cabin" value={form.cabin} onChange={e=>update("cabin",e.target.value as SearchRequest["cabin"])}><option value="ECONOMY">Economy</option><option value="PREMIUM_ECONOMY">Premium economy</option><option value="BUSINESS">Business</option><option value="FIRST">First</option></select></div>
          </div>
          <div className="search-bottom"><fieldset className="travellers"><legend>Travellers</legend>{(["adults","children","infants"] as const).map((key)=><label key={key} htmlFor={key}><span>{key==="adults"?"Adults":key==="children"?"Children":"Infants"}<small>{key==="adults"?"12+ years":key==="children"?"2–11 years":"Under 2"}</small></span><input id={key} type="number" min={key==="adults"?1:0} max={9} value={form[key]} onChange={e=>update(key,Number(e.target.value))}/></label>)}</fieldset>
            <button className="button primary search-button" disabled={mutation.isPending}><Search size={19}/>{mutation.isPending?"Searching…":"Search flights"}<ArrowRight size={18}/></button>
          </div>
          {validation&&<p className="inline-error" role="alert">{validation}</p>}
        </form>
      </div>
    </section>
    <section className="container results-section" aria-label="Flight results" aria-live="polite">
      {mutation.isError&&<ErrorNotice error={mutation.error}/>}
      {(mutation.isPending||(searchId&&results.isPending))&&<Loading/>}
      {results.isError&&<ErrorNotice error={results.error} retry={()=>results.refetch()}/>}
      {results.data&&!mutation.isPending&&<>
        <div className="results-heading"><div><div className="eyebrow">YOUR JOURNEY, YOUR CHOICE</div><h2>{results.data.criteria.origin} <ArrowRight size={23}/> {results.data.criteria.destination}</h2><p>{date(results.data.criteria.departureDate)}{results.data.criteria.returnDate?" · Return "+date(results.data.criteria.returnDate):" · One way"} · {results.data.criteria.adults+results.data.criteria.children+results.data.criteria.infants} traveller(s)</p></div><span className="tag">SIMULATED FLIGHTS</span></div>
        {results.data.partial&&<div className="notice warning" role="status"><strong>Some providers are unavailable.</strong><p>These are partial results. Missing providers may have other options.</p></div>}
        <div className="results-layout"><aside className="filter-card"><h3><SlidersHorizontal size={18}/> Refine your journey</h3>
          <label className="checkbox"><input type="checkbox" checked={filters.nonstop} onChange={e=>changeFilter("nonstop",String(e.target.checked))}/> Non-stop only</label>
          <label htmlFor="airline">Airline</label><select id="airline" value={filters.airline} onChange={e=>changeFilter("airline",e.target.value)}><option value="">All sample airlines</option><option>Aster Air</option><option>Horizon Airways</option></select>
          <label htmlFor="sort">Sort by</label><select id="sort" value={filters.sort} onChange={e=>changeFilter("sort",e.target.value)}><option value="PRICE">Lowest total price</option><option value="DURATION">Shortest duration</option><option value="DEPARTURE">Earliest departure</option></select>
          <button className="text-button" onClick={()=>{setFilters(filtersFrom(new URLSearchParams()));router.push("/?search="+searchId,{scroll:false});}}>Reset filters</button>
          <div className="freshness"><strong>Prices with context</strong><p>Observed {time(results.data.createdAt)} IST. Search expires {time(results.data.expiresAt)} IST. Availability is checked again on selection.</p></div>
        </aside><div className="result-list">
          <p className="result-count">{results.data.total} matching options {results.isFetching&&<span>· Updating…</span>}</p>
          {results.data.offers.length===0?<div className="empty-state"><Search size={28}/><h3>No matching flights</h3><p>Try clearing filters, choosing another date, or searching Delhi to Mumbai.</p></div>:results.data.offers.map(offer=><FlightCard key={offer.id} offer={offer} returnTo={resultUrl} updating={results.isPlaceholderData}/>)}
          {results.data.total>5&&<nav className="pagination" aria-label="Result pages"><button className="button secondary" disabled={filters.page===0} onClick={()=>changeFilter("page",String(filters.page-1))}>Previous</button><span>Page {filters.page+1} of {Math.ceil(results.data.total/5)}</span><button className="button secondary" disabled={(filters.page+1)*5>=results.data.total} onClick={()=>changeFilter("page",String(filters.page+1))}>Next</button></nav>}
          <p className="coverage">Provider coverage: {results.data.coverage.map(c=>c.supplier+" — "+c.status.toLowerCase()).join(" · ")}</p>
        </div></div>
      </>}
    </section>
    {!searchId&&<section className="container inspiration"><div className="section-heading"><div><div className="eyebrow">START WITH A LITTLE INSPIRATION</div><h2>A change of scenery.</h2></div><p>Choose a sample route to begin.</p></div><div className="destination-grid">
      {[{code:"BOM",name:"Mumbai",subtitle:"City lights & sea breezes",className:"mumbai"},{code:"BLR",name:"Bengaluru",subtitle:"Garden mornings & café afternoons",className:"bengaluru"},{code:"GOI",name:"Goa",subtitle:"A slower kind of getaway",className:"goa"}].map((d,i)=><button key={d.code} className={"destination "+d.className} onClick={()=>{setForm(f=>({...f,origin:"DEL",destination:d.code}));document.getElementById("departure")?.focus();}}><span className="destination-art" aria-hidden="true">{i===0?"◒":i===1?"♧":"☀"}</span><span className="destination-content"><small>FROM NEW DELHI</small><strong>{d.name}</strong><span>{d.subtitle}</span></span><span className="round-arrow"><ChevronRight size={20}/></span></button>)}
    </div></section>}
    <section className="container trust-section" id="how-it-works"><div><span className="step-number">01</span><h3>Search with clarity</h3><p>Compare total journey prices, including simulated taxes.</p></div><div><span className="step-number">02</span><h3>Look a little closer</h3><p>Review both directions, baggage and cancellation conditions.</p></div><div><span className="step-number">03</span><h3>Check before committing</h3><p>Revalidate a fare, accept your quote and follow the MOCK booking and payment steps. No real funds or travel.</p></div></section>
  </main>;
}
function FlightCard({offer,returnTo,updating}:{offer:Offer;returnTo:string;updating:boolean}) {
  return <article className="flight-card"><div className="flight-card-top"><span className={"airline-icon "+(offer.supplier==="sample-horizon"?"teal":"")}><Plane size={20}/></span><div><h3>{offer.airline}</h3><span className="muted">{offer.cabin.replaceAll("_"," ")} · Sample schedule</span></div><span className="mock-label">MOCK</span></div>
    {offer.legs.map((leg,index)=><div className="flight-line" key={index}><div><strong>{time(leg.departure)}</strong><span>{leg.origin} · {date(leg.departure)}</span></div><div className="flight-duration"><span>{Math.floor(leg.durationMinutes/60)}h {leg.durationMinutes%60}m</span><div className="route-line"><span/><Plane size={15}/></div><small>{leg.stops===0?"Non-stop":"1 stop"} · IST</small></div><div><strong>{time(leg.arrival)}</strong><span>{leg.destination} · {date(leg.arrival)}</span></div></div>)}
    <div className="flight-card-bottom"><span>Cabin + checked baggage<br/><small>See fare details for conditions</small></span><div className="fare"><strong>{money(offer.total)}</strong><small>Total for {offer.passengers} traveller(s), incl. taxes</small></div>{updating?<span className="button secondary" aria-disabled="true">Updating results…</span>:<Link className="button secondary" href={"/flights/offers/"+offer.id+"?returnTo="+encodeURIComponent(returnTo)}>View flight <ArrowRight size={16}/></Link>}</div>
  </article>;
}

