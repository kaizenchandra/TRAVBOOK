"use client";
import Link from "next/link";
import {useRouter,useSearchParams} from "next/navigation";
import {useEffect,useRef,useState} from "react";
import {useQueryClient} from "@tanstack/react-query";
import {useSession,useCustomer} from "@/features/account/hooks";
import {LoginPanel} from "@/features/account/Account";
import {ErrorNotice,Loading} from "@/components/Feedback";
import {ApiError} from "@/lib/api/client";
import * as h from "./hooks";
const uuid=/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/i;
const pattern='[0-9a-fA-F]{8}-([0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}';
const stamp=(value:string)=>new Date(value).toLocaleString('en-IN',{day:'numeric',month:'short',year:'numeric',hour:'2-digit',minute:'2-digit',timeZoneName:'short'});
export function Sandbox(){
  const session=useSession(),customer=useCustomer(session.data?.authenticated===true&&!session.isError);
  return <main id="main" className="container detail-page"><p className="eyebrow">LOCAL INTEGRATION TOOLS</p><h1>API sandbox</h1>
    <p>Exercise dummy services against your own account. No real message, GPS tracking, account erasure or cloud deployment occurs.</p>
    {session.isPending?<Loading label="Checking your session…"/>:session.isError?<ErrorNotice error={session.error}/>:!session.data?.authenticated?<LoginPanel enabled={session.data?.loginEnabled??false} local={session.data?.identityMode==='LOCAL_OIDC'}/>:customer.isError?<ErrorNotice error={customer.error}/>:customer.data?<OwnedSandbox key={customer.data.id} owner={customer.data.id}/>:<Loading/>}
  </main>;
}
function OwnedSandbox({owner}:{owner:string}){
  const[denied,setDenied]=useState<Error|null>(null);
  if(denied)return <><ErrorNotice error={denied}/><button className="button secondary" onClick={()=>window.location.reload()}>Reload sandbox</button></>;
  return <h.PrivacyFailure.Provider value={setDenied}><Tools owner={owner}/></h.PrivacyFailure.Provider>;
}
function Tools({owner}:{owner:string}){
  const catalog=h.useCatalog(),deployment=h.useDeployment();
  if(catalog.isPending)return <Loading label="Loading dummy APIs…"/>;
  if(catalog.isError)return <><ErrorNotice error={catalog.error} retry={()=>catalog.refetch()}/>{catalog.error instanceof ApiError&&catalog.error.code==='NOT_FOUND'&&<p>Dummy APIs are disabled in this environment. The local Compose setup enables them explicitly.</p>}</>;
  return <><section className="panel"><h2>Travel and payment APIs</h2><p>{catalog.data.notice}</p><div className="demo-services">{catalog.data.services.map(service=><article key={service.name}><h3>{service.name}</h3><p>{service.description}</p><code>{service.api}</code><Link className="button secondary" href={service.route}>Open {service.name.toLowerCase()}</Link></article>)}</div></section>
    <div className="demo-grid"><Operations owner={owner}/><Coupon owner={owner}/><Gps owner={owner}/><Privacy owner={owner}/><Finance owner={owner}/><Payment owner={owner}/></div>
    <section className="panel"><h2>Dummy deployment target</h2>{deployment.isPending?<Loading/>:deployment.isError?<ErrorNotice error={deployment.error} retry={()=>deployment.refetch()}/>:<><p><strong>{deployment.data.target} · {deployment.data.awsStatus}</strong></p><p>{deployment.data.notice}</p></>}</section>
  </>;
}
function Operations({owner}:{owner:string}){
  const params=useSearchParams(),router=useRouter(),cache=useQueryClient();
  const raw=params.get('operation'),id=raw&&uuid.test(raw)?raw:null;
  const[after,setAfter]=useState<string|null>(null),list=h.useDemoOperations(owner,after),selected=h.useDemoOperation(owner,id),create=h.useCreateDemo(owner);
  const[kind,setKind]=useState<h.OperationInput['kind']>('EMAIL'),[scenario,setScenario]=useState<h.OperationInput['scenario']>('NORMAL'),[consent,setConsent]=useState(false);
  const command=useRef<{json:string;key:string}|null>(null);
  useEffect(()=>{if(selected.data?.terminal)void cache.invalidateQueries({queryKey:['demo-operations',owner]});},[selected.data?.id,selected.data?.terminal,cache,owner]);
  function select(value:string){const p=new URLSearchParams(params);p.set('operation',value);router.replace('/sandbox?'+p.toString(),{scroll:false});}
  async function submit(e:React.FormEvent){e.preventDefault();if(!consent)return;const body:h.OperationInput={expectedCustomerId:owner,kind,scenario,consent:true},json=JSON.stringify(body);if(command.current?.json!==json)command.current={json,key:crypto.randomUUID()};
    try{const op=await create.mutateAsync({body,key:command.current!.key});setConsent(false);command.current=null;setAfter(null);select(op.id);}catch{/* Same body/key retained for safe retry. */}}
  return <section className="panel"><h2>Dummy messaging and privacy jobs</h2><p>Email/SMS ends in a local simulated inbox receipt. Privacy jobs require simulated review and never delete data.</p>
    <form onSubmit={submit}><div className="field"><label htmlFor="demo-kind">Simulation type</label><select id="demo-kind" value={kind} onChange={e=>setKind(e.target.value as typeof kind)}><option value="EMAIL">Dummy email</option><option value="SMS">Dummy SMS</option><option value="PRIVACY_EXPORT">Privacy export request</option><option value="PRIVACY_ERASURE">Privacy erasure request</option></select></div>
      <div className="field"><label htmlFor="demo-outcome">Provider behavior</label><select id="demo-outcome" value={scenario} onChange={e=>setScenario(e.target.value as typeof scenario)}><option value="NORMAL">Accept simulation</option><option value="RETRY_ONCE">Fail once, then accept</option><option value="FAIL">Fail after bounded retries</option></select></div>
      <label className="profile-consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/>I understand this queues only a simulation.</label>
      <button className="button primary" disabled={!consent||create.isPending}>{create.isPending?'Queuing…':'Queue simulation'}</button>
    </form>{create.isError&&<ErrorNotice error={create.error}/>}
    {id&&<div className="demo-result" aria-live="polite">{selected.isPending?<Loading label="Loading simulation status…"/>:selected.isError?<ErrorNotice error={selected.error} retry={()=>selected.refetch()}/>:<><h3>Selected operation</h3><code>{selected.data.id}</code><p><strong>{selected.data.status}</strong> · Attempts {selected.data.attempts}/3</p><p>{selected.data.notice}</p><button className="button secondary" disabled={selected.isFetching} onClick={()=>selected.refetch()}>Refresh operation</button></>}</div>}
    <h3>Saved operations</h3>{list.isPending?<Loading/>:list.isError?<ErrorNotice error={list.error} retry={()=>list.refetch()}/>:<>{!list.data.items.length&&<p>No dummy requests yet.</p>}<ul className="demo-history">{list.data.items.map(op=><li key={op.id}><button type="button" className="button secondary" onClick={()=>select(op.id)}>{op.kind} · {op.status}</button><small>{stamp(op.createdAt)} · <code>{op.id}</code></small></li>)}</ul><div className="profile-actions"><button className="button secondary" onClick={()=>{setAfter(null);void list.refetch();}}>Latest requests</button>{list.data.next&&<button className="button secondary" onClick={()=>setAfter(list.data.next)}>Older requests</button>}</div></>}
  </section>;
}
function Coupon({owner}:{owner:string}){
  const params=useSearchParams(),[quoteId,setQuote]=useState(params.get('quote')??''),[code,setCode]=useState('DEMO10'),preview=h.useCouponPreview(owner);
  return <section className="panel"><h2>Coupon preview</h2><p>Use an owned, unexpired flight quote. DEMO10 previews 10% off, minimum ₹1,000, capped at ₹500. It is not applied to checkout.</p>
    <form onSubmit={e=>{e.preventDefault();preview.mutate({quoteId,code});}}><div className="field"><label htmlFor="demo-quote">Flight quote ID</label><input id="demo-quote" required pattern={pattern} value={quoteId} onChange={e=>{setQuote(e.target.value);preview.reset();}}/></div><div className="field"><label htmlFor="demo-coupon">Demo coupon code</label><input id="demo-coupon" required maxLength={32} value={code} onChange={e=>{setCode(e.target.value);preview.reset();}}/></div><button className="button primary" disabled={preview.isPending}>Preview coupon</button></form>
    {preview.isError&&<ErrorNotice error={preview.error}/>} {preview.isPending&&<Loading label="Checking coupon…"/>}
    {preview.data&&!preview.isError&&!preview.isPending&&<div className="demo-result" role="status"><strong>{preview.data.reason.replaceAll('_',' ')}</strong><p>Original ₹{preview.data.originalTotal.amount} · Discount preview ₹{preview.data.discount.amount} · Preview total ₹{preview.data.previewTotal.amount}</p><p>{preview.data.notice}</p></div>}
  </section>;
}
function Gps({owner}:{owner:string}){
  const[input,setInput]=useState(''),[id,setId]=useState<string|null>(null),[consent,setConsent]=useState(false),gps=h.useDemoGps(owner,id),advance=h.useAdvanceGps(id);
  return <section className="panel"><h2>Synthetic cab GPS</h2><p>Positions follow persisted demo stages. No live location or real driver is tracked.</p><form onSubmit={e=>{e.preventDefault();setId(input);setConsent(false);advance.reset();}}><div className="field"><label htmlFor="demo-cab">Owned cab booking ID</label><input id="demo-cab" required pattern={pattern} value={input} onChange={e=>setInput(e.target.value)}/></div><button className="button primary">Load synthetic position</button></form>
    {id&&(gps.isPending?<Loading/>:gps.isError?<ErrorNotice error={gps.error} retry={()=>gps.refetch()}/>:<div className="demo-result" aria-live="polite"><strong>{gps.data.dispatch.stage}</strong><p>{gps.data.latitude?`${gps.data.latitude}, ${gps.data.longitude}`:'No position available.'}</p><p>{gps.data.stale?'Stale or unavailable position.':'Current simulation sample.'} {gps.data.latitude&&`Recorded ${stamp(gps.data.dispatch.updatedAt)}`}</p><p>{gps.data.notice}</p><button className="button secondary" disabled={gps.isFetching} onClick={()=>gps.refetch()}>Refresh position</button>
      {!gps.data.terminal&&<><label className="profile-consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/>Advance the synthetic trip only.</label><button className="button secondary" disabled={!consent||advance.isPending} onClick={async()=>{try{await advance.mutateAsync(gps.data.dispatch.version);setConsent(false);}catch{/* error shown below */}}}>Advance simulation stage</button></>}
      {advance.isError&&<ErrorNotice error={advance.error}/>}</div>)}
  </section>;
}
function Privacy({owner}:{owner:string}){
  const preview=h.usePrivacyPreview(owner);
  return <section className="panel"><h2>Privacy export preview</h2><p>View your current profile only. Bookings, travellers, payments, documents and IdP records are excluded. This is not a complete export.</p><button className="button secondary" disabled={preview.isPending} onClick={()=>preview.mutate()}>Preview my profile data</button>
    {preview.isPending&&<Loading/>}{preview.isError&&<ErrorNotice error={preview.error}/>}{preview.data&&!preview.isError&&!preview.isPending&&<><p>{preview.data.notice}</p><pre className="demo-json" tabIndex={0} aria-label="Profile export preview">{JSON.stringify(preview.data.profile,null,2)}</pre></>}
  </section>;
}
function Finance({owner}:{owner:string}){
  const report=h.useFinancialReport(owner);
  return <section className="panel"><h2>My MOCK ledger report</h2><p>Totals come from your stored payment and refund records. They are not bank balances or settlement reports.</p><button className="button secondary" disabled={report.isPending} onClick={()=>report.mutate()}>Load my ledger report</button>
    {report.isPending&&<Loading/>}{report.isError&&<ErrorNotice error={report.error}/>}{report.data&&!report.isError&&!report.isPending&&<div className="demo-result" role="status"><dl><dt>Captured</dt><dd>₹{report.data.capturedAmount}</dd><dt>Refunded</dt><dd>₹{report.data.refundedAmount}</dd><dt>Refund pending</dt><dd>₹{report.data.pendingRefundAmount}</dd><dt>Net captured</dt><dd>₹{report.data.netCaptured}</dd><dt>Integrity issues</dt><dd>{report.data.integrityIssues}</dd></dl><p>{report.data.accountCount} payment accounts · {report.data.captureCount} captured · {report.data.refundCount} completed refunds</p><strong>{report.data.settlementStatus}</strong><p>{report.data.notice}</p></div>}
  </section>;
}
function Payment({owner}:{owner:string}){
  const[id,setId]=useState(''),lookup=h.useDemoPayment(owner);
  return <section className="panel"><h2>Dummy payment status</h2><p>Read an existing payment account from your booking checkout. Charges and refunds are initiated only through the booking workflows.</p><form onSubmit={e=>{e.preventDefault();lookup.mutate(id);}}><div className="field"><label htmlFor="demo-payment">Payment account ID</label><input id="demo-payment" required pattern={pattern} value={id} onChange={e=>{setId(e.target.value);lookup.reset();}}/></div><button className="button secondary" disabled={lookup.isPending}>Read payment status</button></form>{lookup.isPending&&<Loading/>}{lookup.isError&&<ErrorNotice error={lookup.error}/>} {lookup.data&&!lookup.isError&&!lookup.isPending&&<div role="status" className="demo-result"><strong>{lookup.data.state} · MOCK</strong><p>₹{lookup.data.amount.amount} · refunded ₹{lookup.data.settledRefunds.amount}</p><code>{lookup.data.reference}</code></div>}
  </section>;
}
