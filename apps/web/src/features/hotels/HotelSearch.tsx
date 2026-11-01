"use client";
import {useEffect,useState} from "react";
import Link from "next/link";
import {useRouter,useSearchParams} from "next/navigation";
import {Hotel,Search,ArrowRight,SlidersHorizontal} from "lucide-react";
import {Loading,ErrorNotice} from "@/components/Feedback";
import {date,money,travelDate,time} from "@/lib/format";
import {useHotelSearch,useHotelResults,type HotelSearchRequest,type HotelFilters} from "./hooks";
function filtersFrom(p:Pick<URLSearchParams,"get">):HotelFilters{return {page:Number(p.get("page"))||0,size:5,sort:p.get("sort")==="RATING"?"RATING":"PRICE",type:p.get("type")==="HOTEL"?"HOTEL":p.get("type")==="HOMESTAY"?"HOMESTAY":"ALL",minStars:Number(p.get("minStars"))||0,amenity:p.get("amenity")==="POOL"?"POOL":p.get("amenity")==="PARKING"?"PARKING":p.get("amenity")==="WIFI"?"WIFI":"ALL",refundable:p.get("refundable")==="true",maxTotal:p.get("maxTotal")||"9999999"};}
export function HotelSearch(){
  const params=useSearchParams(),router=useRouter(),id=params.get("search"),filters=filtersFrom(params);
  const [form,setForm]=useState<HotelSearchRequest>(()=>({destination:"Delhi",checkIn:travelDate(7),checkOut:travelDate(9),rooms:[{adults:2,childAges:[]}]}));
  const [error,setError]=useState("");const search=useHotelSearch(),results=useHotelResults(id,filters);
  useEffect(()=>{search.reset();setError("");},[id]);
  useEffect(()=>{if(results.data)setForm(results.data.criteria);},[results.data?.id]);
  function filter(key:string,value:string){const p=new URLSearchParams(params);p.set(key,value);if(key!=="page")p.delete("page");router.push("/hotels?"+p,{scroll:false});}
  function room(index:number,adults:number,childAges:number[]){setForm(f=>({...f,rooms:f.rooms.map((r,i)=>i===index?{adults,childAges}:r)}));}
  async function submit(e:React.FormEvent){e.preventDefault();setError("");if(form.checkOut<=form.checkIn){setError("Check-out must be after check-in.");return;}try{const data=await search.mutateAsync(form);router.push("/hotels?search="+data.id,{scroll:false});}catch{/* Visible mutation error. */}}
  const data=results.data;const returnUrl="/hotels?"+params;
  return <main id="main" className="hotel-page">
    <section className="hotel-hero"><div className="container"><p className="eyebrow">ROOM TO UNWIND</p><h1>Find your next hotel or homestay.</h1><p>Compare rooms, thoughtful details and the full cost of your stay.</p><span className="tag">MOCK properties · No real reservations or payments</span></div></section>
    <div className="container">
      <nav className="hotel-service-links" aria-label="Travel services"><Link href="/">Flights</Link><Link href="/hotels" aria-current="page"><Hotel size={18}/> Hotels &amp; Homestays</Link><Link href="/trains">Trains</Link></nav>
      <form className="panel hotel-search-form" onSubmit={submit} aria-label="Find hotels">
        <div className="hotel-form-grid"><label>Destination<input required list="hotel-destinations" minLength={2} maxLength={80} value={form.destination} onChange={e=>setForm(f=>({...f,destination:e.target.value}))}/><datalist id="hotel-destinations">{["Delhi","Mumbai","Goa","Bengaluru"].map(city=><option key={city} value={city}/>)}</datalist></label>
          <label>Check-in<input type="date" required min={travelDate()} max={travelDate(329)} value={form.checkIn} onChange={e=>setForm(f=>({...f,checkIn:e.target.value}))}/></label>
          <label>Check-out<input type="date" required min={form.checkIn} max={travelDate(330)} value={form.checkOut} onChange={e=>setForm(f=>({...f,checkOut:e.target.value}))}/></label>
          <label>Rooms<select value={form.rooms.length} onChange={e=>setForm(f=>({...f,rooms:Array.from({length:Number(e.target.value)},(_,i)=>f.rooms[i]??{adults:2,childAges:[]})}))}>{[1,2,3,4].map(n=><option key={n}>{n}</option>)}</select></label></div>
        <div className="hotel-occupancy">{form.rooms.map((r,i)=><fieldset key={i}><legend>Room {i+1}</legend><label>Adults in room {i+1}<select value={r.adults} onChange={e=>room(i,Number(e.target.value),r.childAges)}>{[1,2,3].map(n=><option key={n}>{n}</option>)}</select><small>Age 18+</small></label><label>Children in room {i+1}<select value={r.childAges.length} onChange={e=>room(i,r.adults,Array.from({length:Number(e.target.value)},(_,j)=>r.childAges[j]??5))}>{[0,1,2].map(n=><option key={n}>{n}</option>)}</select><small>Age 0–17 at check-in</small></label>{r.childAges.map((age,j)=><label key={j}>Room {i+1}, child {j+1} age<input type="number" required min={0} max={17} value={age} onChange={e=>room(i,r.adults,r.childAges.map((a,k)=>k===j?Number(e.target.value):a))}/></label>)}</fieldset>)}</div>
        <div className="hotel-search-bottom"><p>1–30 nights · 1–4 rooms · Child ages affect availability and price.</p><button className="button primary" disabled={search.isPending}><Search size={18}/>{search.isPending?"Searching stays…":"Search hotels"}</button></div>
        {error&&<p role="alert" className="inline-error">{error}</p>}
      </form>
      {search.isError&&<ErrorNotice error={search.error}/>}
      {(search.isPending||id&&results.isPending)&&<Loading label="Checking sample hotel providers…"/>}
      {results.isError&&<ErrorNotice error={results.error} retry={()=>results.refetch()}/>}
      {data&&!results.isError&&!search.isPending&&!search.isError&&<section aria-label="Hotel results">
        <div className="results-heading"><div><p className="eyebrow">FIND YOUR PLACE</p><h2>{data.criteria.destination}</h2><p>{date(data.criteria.checkIn)} – {date(data.criteria.checkOut)} · {data.criteria.rooms.length} room(s)</p></div><strong role="status">{data.total} matching properties</strong></div>
        {data.partial&&<div className="notice warning" role="status"><strong>Some hotel providers are unavailable.</strong><p>These are partial results; unavailable providers are not reported as sold out.</p></div>}
        <div className="hotel-results-layout"><aside className="panel hotel-filters" aria-label="Hotel filters"><h3><SlidersHorizontal size={17}/> Refine your stay</h3>
          <label>Sort stays<select value={filters.sort} onChange={e=>filter("sort",e.target.value)}><option value="PRICE">Lowest total stay cost</option><option value="RATING">Highest sample rating</option></select></label>
          <label>Property type<select value={filters.type} onChange={e=>filter("type",e.target.value)}><option value="ALL">All stays</option><option value="HOTEL">Hotels</option><option value="HOMESTAY">Homestays</option></select></label>
          <label>Minimum stars<select value={filters.minStars} onChange={e=>filter("minStars",e.target.value)}>{[0,3,4,5].map(n=><option key={n} value={n}>{n===0?"Any stars":n+"+ sample stars"}</option>)}</select></label>
          <label>Amenity<select value={filters.amenity} onChange={e=>filter("amenity",e.target.value)}><option value="ALL">Any amenities</option><option value="WIFI">Wi-Fi</option><option value="POOL">Pool</option><option value="PARKING">Parking</option></select></label>
          <label>Maximum total (INR)<input type="number" min={0} max={9999999} step="0.01" value={filters.maxTotal} onChange={e=>filter("maxTotal",e.target.value)}/></label>
          <label className="hotel-check"><input type="checkbox" checked={filters.refundable} onChange={e=>filter("refundable",String(e.target.checked))}/> Refundable options for every room</label>
          <button className="button secondary" onClick={()=>router.push("/hotels?search="+id,{scroll:false})}>Reset filters</button><p>Observed {time(data.createdAt)} IST. Search expires {time(data.expiresAt)} IST.</p>
        </aside><div>{data.items.length===0?<div className="empty-state"><h3>No matching stays</h3><p>Change the destination, dates or filters and try again.</p></div>:data.items.map(r=><article className="hotel-card" key={r.property.id}>
          <img src={r.property.images[0].url} alt={r.property.images[0].alt} width={400} height={300}/><div className="hotel-card-content"><span className="tag">{r.property.type==="HOTEL"?"Sample hotel":"Sample homestay"}</span><h3>{r.property.name}</h3><p>{r.property.locality}, {r.property.city}</p><p>{r.property.stars} sample stars · {r.property.rating}/5 sample rating · {r.property.reviewCount} guest reviews</p><p className="hotel-amenities">{r.property.amenities.join(" · ")}</p><p>{r.refundableAvailable?"Refundable plans available":"Non-refundable plans"}</p><div className="hotel-card-action"><div><small>From, all rooms &amp; nights</small><strong>{money(r.fromTotal)}</strong><small>Includes simulated taxes &amp; fees</small></div><Link className="button primary" href={`/hotels/${r.property.id}?search=${id}&from=${encodeURIComponent(returnUrl)}`}>Compare rooms <ArrowRight size={16}/></Link></div></div>
        </article>)}{data.total>data.size&&<div className="pagination"><button className="button secondary" disabled={data.page===0} onClick={()=>filter("page",String(data.page-1))}>Previous</button><span>Page {data.page+1}</span><button className="button secondary" disabled={(data.page+1)*data.size>=data.total} onClick={()=>filter("page",String(data.page+1))}>Next</button></div>}</div></div>
      </section>}
      {!id&&<section className="hotel-discovery panel"><h2>A clearer view of your stay</h2><p>Explore fictional properties in Delhi, Mumbai, Goa and Bengaluru. Compare breakfast, room capacity and cancellation deadlines before reviewing current rates.</p><p>Illustrations and ratings are synthetic. Owned quotes, guest checkout, simulated reservations/payments and protected MOCK vouchers are available after room selection.</p></section>}
    </div>
  </main>;
}
