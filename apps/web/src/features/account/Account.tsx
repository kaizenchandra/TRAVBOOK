"use client";
import Link from "next/link";
import {useSearchParams} from "next/navigation";
import {useSession,useCustomer} from "./hooks";
import {Loading,ErrorNotice} from "@/components/Feedback";
import {ProfileEditor} from "./ProfileEditor";
export function Account() {
  const session=useSession(); const customer=useCustomer(session.data?.authenticated===true);const params=useSearchParams();
  return <main id="main" className="container detail-page"><div className="detail-title"><div className="eyebrow">YOUR TRAVEL SPACE</div><h1>Your account</h1><p>Sign in to keep quotations private and review your choices.</p></div>
    {session.isPending&&<Loading label="Checking your session…"/>}
    {session.isError&&<ErrorNotice error={session.error} retry={()=>session.refetch()}/>}
    {params.get("loginError")&&<div role="alert" className="notice error">Sign-in could not be completed. Start a new sign-in attempt.</div>}
    {params.get("loggedOut")&&<p role="status" className="notice success">You have signed out.</p>}
    {session.data&&!session.data.authenticated&&<LoginPanel enabled={session.data.loginEnabled} local={session.data.identityMode==="LOCAL_OIDC"}/>}
    {session.data?.authenticated&&!session.isError&&<section className="panel account-panel"><h2>You’re signed in</h2>
      {customer.isPending&&<Loading label="Loading your account…"/>}{customer.isError&&<ErrorNotice error={customer.error}/>}
      {customer.data&&!customer.isError&&<><p>Customer reference: <code>{customer.data.id}</code></p><ProfileEditor key={customer.data.id} owner={customer.data.id}/></>}
      <p>Your quotations are visible only to your authenticated account. Travel products remain simulated in this preview.</p>
      <Link className="button primary" href="/">Explore flights</Link>
      <Link className="button secondary logout-form" href="/trips">My Trips</Link>
      <Link className="button secondary logout-form" href="/sandbox">API sandbox</Link>
      <Link className="button secondary logout-form" href="/account/travel">Saved travel details</Link>
      <Link className="button secondary logout-form" href="/notifications">Notifications</Link>
      <Link className="button secondary logout-form" href="/flight-services">Flight seats & extras</Link>
      <form action="/auth/logout" method="post" className="logout-form"><input type="hidden" name="_csrf" value={session.data.csrfToken}/><button className="button secondary">Sign out</button></form>
    </section>}
  </main>;
}
export function LoginPanel({enabled,local,offerId,requestId,quoteId,bookingId,bookingQuoteId,stay}:{enabled:boolean;local:boolean;offerId?:string;requestId?:string;quoteId?:string;bookingId?:string;bookingQuoteId?:string;stay?:string}) {
  const params=new URLSearchParams();if(offerId)params.set("offerId",offerId);if(requestId)params.set("requestId",requestId);if(quoteId)params.set("quoteId",quoteId);
  if(bookingId)params.set("bookingId",bookingId);if(bookingQuoteId)params.set("bookingQuoteId",bookingQuoteId);
  if(stay)params.set("stay",stay);
  return <section className="panel account-panel"><h2>Sign in to continue</h2>
    <p>Your identity provider handles sign-in. Travbook never asks for your password.</p>
    {local&&<div className="notice warning"><strong>Local test identity</strong><p>Use the local test account documented in the setup guide. This is not a production account service.</p></div>}
    {enabled?<a className="button primary" href={"/auth/login?"+params.toString()}>Continue to secure sign-in</a>:<p role="status" className="notice warning">Sign-in is not configured. Start the local OIDC profile to try authenticated quotations.</p>}
    <p><Link className="back-link" href="/">Continue exploring without signing in</Link></p>
  </section>;
}

