"use client";
import Link from "next/link";
import {useParams,useRouter,useSearchParams} from "next/navigation";
import {useEffect,useState} from "react";
import {useSession,useCustomer} from "@/features/account/hooks";
import {LoginPanel} from "@/features/account/Account";
import {useCreateQuote,useQuote,useAcceptQuote} from "./hooks";
import {useOffer} from "@/features/flights/hooks";
import {ApiError} from "@/lib/api/client";
import {date,money,time} from "@/lib/format";
import {ErrorNotice,Loading} from "@/components/Feedback";
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export function NewQuote() {
  const params=useSearchParams();const router=useRouter();const offerId=params.get("offerId")??"";const requestId=params.get("requestId")??"";
  const session=useSession();const offer=useOffer(offerId);const create=useCreateQuote();
  useEffect(()=>{if(uuid.test(offerId)&&!uuid.test(requestId))router.replace("/quotes/new?offerId="+offerId+"&requestId="+crypto.randomUUID());},[offerId,requestId,router]);
  return <main id="main" className="container detail-page"><div className="detail-title"><div className="eyebrow">REVIEW BEFORE YOU COMMIT</div><h1>Your personal quotation</h1><p>A current price, saved privately to your account.</p></div>
    {!uuid.test(offerId)?<div role="alert" className="notice error">Select a valid flight first. <Link href="/">Search flights</Link></div>:<>
    {session.isPending&&<Loading label="Checking your session…"/>}{session.isError&&<ErrorNotice error={session.error} retry={()=>session.refetch()}/>}
    {session.data&&!session.data.authenticated&&<LoginPanel enabled={session.data.loginEnabled} local={session.data.identityMode==="LOCAL_OIDC"} offerId={offerId} requestId={requestId}/>}
    {session.data?.authenticated&&<section className="panel account-panel">
      {offer.isPending&&<Loading label="Loading your selected flight…"/>}{offer.isError&&<><ErrorNotice error={offer.error}/><Link className="button secondary" href="/">Start a new search</Link></>}
      {offer.data&&<><h2>{offer.data.legs[0].origin} → {offer.data.legs[0].destination}</h2><p>{offer.data.airline} · {date(offer.data.legs[0].departure)} · {offer.data.passengers} traveller(s)</p>
        <p>Search price: <strong>{money(offer.data.total)}</strong>. We’ll check current availability and price before creating your quote.</p>
        <button className="button primary" disabled={create.isPending||!uuid.test(requestId)} onClick={async()=>{try{const q=await create.mutateAsync({offerId,key:requestId});router.replace("/quotes/"+q.id);}catch{/* visible below */}}}>{create.isPending?"Preparing your quote…":"Create my quote"}</button>
        <p className="honesty-note">No reservation, inventory hold or payment is made.</p>
      </>}
      {create.isError&&<ErrorNotice error={create.error}/>}
      {create.error instanceof ApiError&&create.error.code==="UNAUTHENTICATED"&&<a className="button secondary" href={"/auth/login?offerId="+offerId+"&requestId="+requestId}>Sign in again</a>}
    </section>}</>}
  </main>;
}
export function QuoteReview() {
  const {id}=useParams<{id:string}>();const session=useSession();const customer=useCustomer(session.data?.authenticated===true);
  const quote=useQuote(id,session.data?.authenticated?customer.data?.id:undefined);const accept=useAcceptQuote(id,customer.data?.id);
  const [reviewed,setReviewed]=useState(false);const [now,setNow]=useState(Date.now());
  useEffect(()=>{const timer=setInterval(()=>setNow(Date.now()),1000);return()=>clearInterval(timer);},[]);
  const q=quote.isError?undefined:quote.data?.quote; const expired=quote.data?.expired||!!q&&Date.parse(q.expiresAt)<=now;
  return <main id="main" className="container detail-page"><Link className="back-link" href="/">Back to discovery</Link><div className="detail-title"><div className="eyebrow">YOUR CHOICE, CLEARLY RECORDED</div><h1>Review your quotation</h1></div>
    {session.isPending&&<Loading label="Checking your session…"/>}{session.isError&&<ErrorNotice error={session.error}/>}
    {session.data&&!session.data.authenticated&&<LoginPanel enabled={session.data.loginEnabled} local={session.data.identityMode==="LOCAL_OIDC"} quoteId={id}/>}
    {session.data?.authenticated&&<>
      {(customer.isPending||quote.isPending)&&<Loading label="Loading your private quotation…"/>}
      {customer.isError&&<ErrorNotice error={customer.error}/>}
      {quote.isError&&<ErrorNotice error={quote.error} retry={()=>quote.refetch()}/>}
      {q&&<div className="detail-layout"><section className="panel"><span className="tag">MOCK QUOTE · VERSION {q.version}</span><h2 className="quote-title">{q.airline}</h2>
        {q.legs.map((leg,i)=><div className="itinerary-leg" key={i}><h3>{i===0?"Outbound":"Return"}: {leg.origin} → {leg.destination}</h3><p>{date(leg.departure)} · {time(leg.departure)}–{time(leg.arrival)} IST</p></div>)}
        <p>{q.passengers} traveller(s) · {q.cabin.replaceAll("_"," ")}</p><h3>Baggage</h3><p>{q.baggage}</p><h3>Cancellation conditions</h3><p>{q.cancellationPolicy}</p>
        <p className="quote-reference">Quote reference: <code>{q.id}</code></p>
      </section><aside className="panel price-panel"><h2>Your complete fare</h2><dl><div><dt>Base fare</dt><dd>{money(q.base)}</dd></div><div><dt>Simulated taxes</dt><dd>{money(q.taxes)}</dd></div><div className="total"><dt>Total</dt><dd>{money(q.total)}</dd></div></dl>
        {q.priceChanged&&<div className="notice warning" role="status"><strong>The price changed.</strong><p>Search price {money(q.previousTotal)}. Your quoted total is {money(q.total)}. {quote.data?.acceptance?"You accepted this updated amount.":"Review and explicitly accept this amount."}</p></div>}
        <p>Created {time(q.createdAt)} IST. Valid until {time(q.expiresAt)} IST.</p>
        {expired&&<div className="notice warning" role="status"><strong>This quotation has expired.</strong><p>Its original price remains recorded. A new quote needs fresh availability and price checks.</p><Link className="button secondary" href={"/quotes/new?offerId="+q.offerId}>Request a fresh quote</Link></div>}
        {quote.data?.acceptance?<div className="notice success" role="status"><strong>Quote accepted</strong><p>Version {quote.data.acceptance.quoteVersion} accepted at {time(quote.data.acceptance.acceptedAt)} IST. This is not a booking confirmation.</p></div>:!expired&&<>
          <label className="quote-consent"><input type="checkbox" checked={reviewed} onChange={e=>setReviewed(e.target.checked)}/><span>I accept the quoted total of {money(q.total)} and the displayed conditions.</span></label>
          <button className="button primary" disabled={!reviewed||accept.isPending} onClick={()=>accept.mutate({version:q.version,key:"accept-"+q.id+"-v"+q.version})}>{accept.isPending?"Recording acceptance…":"Accept this quote"}</button>
        </>}
        {accept.isError&&<ErrorNotice error={accept.error} retry={()=>quote.refetch()}/>}
        {quote.data?.acceptance&&!expired&&<Link className="button primary quote-link" href={"/bookings/new?quoteId="+q.id}>Add travellers and create intent</Link>}
        <p className="honesty-note">Acceptance records your choice only. You can create a durable booking intent next; supplier reservation and payments are not enabled.</p>
      </aside></div>}
    </>}
  </main>;
}

