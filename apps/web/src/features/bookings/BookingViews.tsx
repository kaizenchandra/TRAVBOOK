"use client";
import {RailTicketTripList} from "@/features/rail-tickets/RailTicketViews";
import {StayTripList} from "@/features/stays/StayViews";
import Link from "next/link";
import {useParams,useRouter,useSearchParams} from "next/navigation";
import {useEffect,useState} from "react";
import {useSession,useCustomer} from "@/features/account/hooks";
import {LoginPanel} from "@/features/account/Account";
import {useBookingRequirements,useCreateBooking,useBooking,useBookings,type Traveller} from "./hooks";
import {Loading,ErrorNotice} from "@/components/Feedback";
import {date,money,time} from "@/lib/format";
import {ApiError} from "@/lib/api/client";
import {BookingTimeline} from "./BookingTimeline";
import {CheckoutPanel,checkoutLabel} from "./CheckoutPanel";
import {CancellationPanel,cancellationLabel} from "./CancellationPanel";
import {TripDocuments} from "./TripDocuments";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function NewBooking() {
  const params=useSearchParams(),router=useRouter();const quoteId=params.get("quoteId")??"",requestId=params.get("requestId")??"";
  const session=useSession(),customer=useCustomer(session.data?.authenticated===true);
  const requirements=useBookingRequirements(quoteId,session.data?.authenticated?customer.data?.id:undefined);const create=useCreateBooking();
  const [travellers,setTravellers]=useState<Traveller[]>([]);const [consent,setConsent]=useState(false);
  useEffect(()=>{if(uuid.test(quoteId)&&!uuid.test(requestId))router.replace("/bookings/new?quoteId="+quoteId+"&requestId="+crypto.randomUUID());},[quoteId,requestId,router]);
  const r=requirements.isError?undefined:requirements.data;
  const authLost=[customer.error,requirements.error].some(e=>e instanceof ApiError&&e.code==="UNAUTHENTICATED");
  useEffect(()=>{if(r&&!r.existingBookingId)setTravellers(Array.from({length:r.adults+r.children+r.infants},()=>({givenName:"",familyName:"",dateOfBirth:""})));},[r?.quoteId]);
  function update(index:number,key:keyof Traveller,value:string){setTravellers(old=>old.map((t,i)=>i===index?{...t,[key]:value}:t));}
  async function submit(event:React.FormEvent) {
    event.preventDefault();if(!r||!consent)return;
    try{const ack=await create.mutateAsync({body:{quoteId:r.quoteId,quoteVersion:r.quoteVersion,travellers},key:requestId});router.replace("/bookings/"+ack.id);}catch{/* No automatic mutation retry; the same key remains in the URL. */}
  }
  return <main id="main" className="container detail-page"><Link href={"/quotes/"+quoteId} className="back-link">Back to quotation</Link>
    <div className="detail-title"><div className="eyebrow">A RECOVERABLE NEXT STEP</div><h1>Traveller details</h1><p>Create a durable booking intent. No reservation or payment will be made.</p></div>
    {!uuid.test(quoteId)&&<div className="notice error" role="alert">Select and accept a quotation first. <Link href="/">Explore flights</Link></div>}
    {session.isPending&&<Loading label="Checking your session…"/>}{session.isError&&<ErrorNotice error={session.error}/>}
    {session.data&&(!session.data.authenticated||authLost)&&<LoginPanel enabled={session.data.loginEnabled} local={session.data.identityMode==="LOCAL_OIDC"} bookingQuoteId={quoteId} requestId={requestId}/>}
    {session.data?.authenticated&&!authLost&&<>
      {(customer.isPending||requirements.isPending)&&<Loading label="Checking your accepted quote…"/>}
      {customer.isError&&<ErrorNotice error={customer.error}/>}
      {requirements.isError&&<><ErrorNotice error={requirements.error} retry={()=>requirements.refetch()}/><Link className="button secondary" href="/">Start a new search</Link></>}
      {r?.existingBookingId?<section className="panel"><h2>Your booking intent is already saved</h2><p>Continue from its authoritative status. No new intent was submitted.</p><Link className="button primary" href={"/bookings/"+r.existingBookingId}>View saved booking intent</Link></section>:r&&<form onSubmit={submit} className="panel">
        <span className="tag">MOCK · ACCEPTED QUOTE VERSION {r.quoteVersion}</span>
        <h2 className="quote-title">{r.adults} adult(s), {r.children} child(ren), {r.infants} infant(s)</h2>
        <p>Age is checked on every departure: adults 12+, children 2–11, infants under 2. The quote expires at {time(r.expiresAt)} IST.</p>
        <div className="notice warning">Use fictional traveller details in this local MOCK preview. Names and birth dates stay out of URLs and browser storage.</div>
        {travellers.map((t,index)=><fieldset className="traveller-form" key={index}><legend>Traveller {index+1} · {index<r.adults?"Adult":index<r.adults+r.children?"Child":"Infant"}</legend>
          <label htmlFor={"given-"+index}>Given name<input id={"given-"+index} required maxLength={80} autoComplete="off" value={t.givenName} onChange={e=>update(index,"givenName",e.target.value)}/></label>
          <label htmlFor={"family-"+index}>Family name<input id={"family-"+index} required maxLength={80} autoComplete="off" value={t.familyName} onChange={e=>update(index,"familyName",e.target.value)}/></label>
          <label htmlFor={"birth-"+index}>Date of birth<input id={"birth-"+index} type="date" required max={r.departureDate} value={t.dateOfBirth} onChange={e=>update(index,"dateOfBirth",e.target.value)}/></label>
        </fieldset>)}
        <label className="quote-consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/><span>I reviewed these travellers. Create a MOCK booking intent and recheck the accepted fare. I understand this does not reserve or confirm travel.</span></label>
        <button className="button primary" disabled={!consent||create.isPending||!uuid.test(requestId)}>{create.isPending?"Saving your intent…":"Create booking intent"}</button>
        {create.isError&&<><ErrorNotice error={create.error}/><button className="button secondary" type="button" onClick={()=>requirements.refetch()}>Check whether my intent was saved</button></>}
      </form>}
    </>}
  </main>;
}
const labels:Record<string,string>={CREATED:"Intent saved",VALIDATING:"Checking your selected offer",AWAITING_PAYMENT:"Validation complete — awaiting payment",REQUIRES_REQUOTE:"A new quotation is required",INVENTORY_UNAVAILABLE:"The offer is no longer available",EXPIRED:"The quotation expired",MANUAL_REVIEW:"Validation needs review"};
export function BookingStatus() {
  const {id}=useParams<{id:string}>();const session=useSession(),customer=useCustomer(session.data?.authenticated===true);
  const [historyError,setHistoryError]=useState<Error|null>(null);
  useEffect(()=>setHistoryError(null),[id,customer.data?.id]);
  const result=useBooking(id,session.data?.authenticated?customer.data?.id:undefined);const details=result.isError||historyError?undefined:result.data,b=details?.booking;
  const authLost=[customer.error,result.error,historyError].some(e=>e instanceof ApiError&&e.code==="UNAUTHENTICATED");
  function refreshPrivate(){void result.refetch().then(updated=>{if(updated.isSuccess)setHistoryError(null);});}
  return <main id="main" className="container detail-page"><Link className="back-link" href="/trips">My Trips</Link><div className="detail-title"><div className="eyebrow">YOUR REQUEST, SAFELY RECORDED</div><h1>Booking intent status</h1><p>These are simulated booking-intent states, not confirmed travel.</p></div>
    {session.isPending&&<Loading label="Checking your session…"/>}{session.isError&&<ErrorNotice error={session.error}/>}
    {session.data&&(!session.data.authenticated||authLost)&&<LoginPanel enabled={session.data.loginEnabled} local={session.data.identityMode==="LOCAL_OIDC"} bookingId={id}/>}
    {session.data?.authenticated&&!authLost&&<>
      {(customer.isPending||result.isPending)&&<Loading label="Loading your booking intent…"/>}{customer.isError&&<ErrorNotice error={customer.error}/>}
      {result.isError&&<ErrorNotice error={result.error} retry={refreshPrivate}/>}
      {!result.isError&&historyError&&<ErrorNotice error={historyError} retry={refreshPrivate}/>}
      {b&&details&&<div className="detail-layout"><section className="panel">
        <span className="tag">MOCK BOOKING INTENT</span><h2 className="quote-title">{b.origin} → {b.destination}</h2><p>{date(b.departure)} · {time(b.departure)} IST · {b.passengers} traveller(s)</p>
        <div className={"notice "+(b.status==="AWAITING_PAYMENT"?"success":"warning")} role="status"><strong>{details.checkout?"Offer validation completed":labels[b.status]}</strong>
          {b.status==="AWAITING_PAYMENT"?<p>Price, policies and availability matched at validation. {details.checkout?"See the separate checkout states below for subsequent activity.":"You can now explicitly start MOCK checkout. Nothing has been held, charged or confirmed."}</p>:
          details.workflow.terminal?<p>No reservation or charge was made. {b.failureCode==="PRICE_CHANGED"||b.failureCode==="TERMS_CHANGED"?"Review and accept a new quote before starting another intent.":"Return to discovery to choose a new offer if needed."}</p>:<p>Your request is stored. Validation can resume after an interruption without creating a second intent.</p>}
        </div>
        <dl className="booking-states"><div><dt>{details.cancellation?"Supplier cancellation":"Supplier reservation"}</dt><dd>{(details.cancellation?.cancellationStatus??details.checkout?.reservationStatus??"NOT_REQUESTED").replaceAll("_"," ")}</dd></div><div><dt>{details.cancellation?"Original payment":"Payment"}</dt><dd>{(details.checkout?.paymentStatus??"NOT_STARTED").replaceAll("_"," ")}</dd></div><div><dt>Inventory check</dt><dd>{details.inventoryStatus.replaceAll("_"," ")}</dd></div><div><dt>Validation attempts</dt><dd>{details.workflow.attempts}</dd></div></dl>
        <p className="quote-reference">Intent reference: <code>{b.id}</code></p><p>Updated {time(b.updatedAt)} IST · version {b.version}</p>
        {(!details.workflow.terminal||details.checkout&&!details.checkout.terminal||details.cancellation&&!details.cancellation.terminal)&&<p className="muted">Updates use bounded backoff and pause in the background. If automatic checks stop, refresh the status manually.</p>}
        <button className="button secondary" disabled={result.isFetching} onClick={()=>result.refetch()}>Refresh status</button>
        <p><Link className="back-link" href={"/quotes/"+b.quoteId}>View original quotation</Link></p>
        {(b.status==="REQUIRES_REQUOTE"||b.status==="EXPIRED")&&<Link className="button secondary" href={"/quotes/new?offerId="+details.offerId}>Request a fresh quotation</Link>}
        <CheckoutPanel key={"checkout:"+b.id} id={b.id} checkout={details.checkout} canStart={b.status==="AWAITING_PAYMENT"} refresh={refreshPrivate} onAccessLost={setHistoryError} aftercare={!!details.cancellation}/>
        {details.checkout?.status==="CONFIRMED"&&(!details.cancellation||details.cancellation.cancellationStatus==="REJECTED")&&customer.data&&<TripDocuments key={customer.data.id+":"+b.id} bookingId={b.id} customerId={customer.data.id} onAccessLost={setHistoryError}/>}
        {details.checkout?.status==="CONFIRMED"&&<CancellationPanel key={"cancellation:"+b.id} id={b.id} state={details.cancellation} refresh={refreshPrivate} onAccessLost={setHistoryError}/>}
        {customer.data&&<BookingTimeline id={b.id} version={b.version} customerId={customer.data.id} onAccessLost={setHistoryError}/>}
      </section><aside className="panel price-panel"><h2>Accepted journey total</h2><strong className="booking-total">{money(b.total)}</strong><p>No real payment is collected. Simulated payment activity is shown separately.</p><h3>Travellers</h3>
        <ul className="booking-travellers">{details.travellers.map((t,i)=><li key={i}><strong>{t.givenName} {t.familyName}</strong><span>Date of birth: {t.dateOfBirth}</span></li>)}</ul>
        <p>Quote valid until {time(details.quoteExpiresAt)} IST. Any future payment/reservation must revalidate eligibility, expiry and inventory.</p>
        <Link className="button secondary" href="/">Explore another journey</Link>
      </aside></div>}
    </>}
  </main>;
}
export function MyTrips() {
  const session=useSession(),customer=useCustomer(session.data?.authenticated===true);const trips=useBookings(session.data?.authenticated?customer.data?.id:undefined);
  const pages=trips.isError?undefined:trips.data?.pages;
  const authLost=[customer.error,trips.error].some(e=>e instanceof ApiError&&e.code==="UNAUTHENTICATED");
  return <main id="main" className="container detail-page"><div className="detail-title"><div className="eyebrow">YOUR TRAVEL SPACE</div><h1>My Trips</h1><Link className="button secondary" href="/flight-services">Seats & extras bookings</Link><Link className="button secondary" href="/journeys/trips">Bus, holiday and cab bookings</Link><p>Saved intents and simulated checkout outcomes. No real travel, charges or tickets.</p></div>
    {session.isPending&&<Loading label="Checking your session…"/>}{session.isError&&<ErrorNotice error={session.error}/>}
    {session.data&&(!session.data.authenticated||authLost)&&<LoginPanel enabled={session.data.loginEnabled} local={session.data.identityMode==="LOCAL_OIDC"}/>}
    {session.data?.authenticated&&!authLost&&<>{(customer.isPending||trips.isPending)&&<Loading label="Loading your saved intents…"/>}{customer.isError&&<ErrorNotice error={customer.error}/>}
      {trips.isError&&<ErrorNotice error={trips.error} retry={()=>trips.refetch()}/>}
      {customer.data&&<StayTripList key={customer.data.id} owner={customer.data.id}/>}
      {customer.data&&<RailTicketTripList key={"rail"+customer.data.id} owner={customer.data.id}/>}
      {pages?.[0].items.length===0&&<section className="panel"><h2>No booking intents yet</h2><p>Find a flight, accept a quotation and add travellers to start.</p><Link className="button primary" href="/">Explore flights</Link></section>}
      {pages?.flatMap(page=>page.items).map(b=><article className="panel trip-intent" key={b.id}><div><span className="tag">MOCK INTENT</span><h2 className="quote-title">{b.origin} → {b.destination}</h2><p>{date(b.departure)} · {b.passengers} traveller(s)</p><strong>{b.cancellation?cancellationLabel[b.cancellation.cancellationStatus]:b.checkout?checkoutLabel[b.checkout.status]:labels[b.status]}</strong></div><div><p className="booking-total">{money(b.total)}</p><Link className="button secondary" href={"/bookings/"+b.id}>View booking intent</Link></div></article>)}
      {trips.hasNextPage&&<button className="button secondary" disabled={trips.isFetchingNextPage} onClick={()=>trips.fetchNextPage()}>Load more intents</button>}
    </>}
  </main>;
}

