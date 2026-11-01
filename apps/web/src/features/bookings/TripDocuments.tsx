"use client";
import {useEffect,useRef} from "react";
import {useMutation,useQuery,useQueryClient} from "@tanstack/react-query";
import {api,unwrap,ApiError} from "@/lib/api/client";
import {csrfHeaders} from "@/features/account/hooks";
import {Loading,ErrorNotice} from "@/components/Feedback";
import {date} from "@/lib/format";
export function TripDocuments({bookingId,customerId,onAccessLost}:{bookingId:string;customerId:string;onAccessLost:(error:Error)=>void}) {
  const client=useQueryClient(),key=useRef<string|null>(null);
  const query=useQuery({queryKey:["trip-documents",customerId,bookingId],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/bookings/{id}/documents",{params:{path:{id:bookingId}},signal})),retry:false,staleTime:0});
  const create=useMutation({mutationFn:async()=>{key.current??=crypto.randomUUID();return unwrap(await api.POST("/api/v1/bookings/{id}/documents",{params:{path:{id:bookingId},header:{"Idempotency-Key":key.current}},body:{kind:"MOCK_CONFIRMATION_SUMMARY"},headers:await csrfHeaders()}));},retry:false,onSuccess:()=>client.invalidateQueries({queryKey:["trip-documents",customerId,bookingId]})});
  const download=useMutation({mutationFn:async(documentId:string)=>{
    const blob=unwrap(await api.GET("/api/v1/bookings/{id}/documents/{documentId}/content",{params:{path:{id:bookingId,documentId}},parseAs:"blob"}));
    const url=URL.createObjectURL(blob),anchor=document.createElement("a");anchor.href=url;anchor.download="travbook-MOCK-summary-"+documentId+".txt";anchor.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
  },retry:false});
  const error=query.error??create.error??download.error;
  useEffect(()=>{if(error instanceof ApiError&&["UNAUTHENTICATED","FORBIDDEN","NOT_FOUND"].includes(error.code))onAccessLost(error);},[error,onAccessLost]);
  const items=query.isError?undefined:query.data?.items;
  return <section className="booking-timeline" aria-labelledby="documents-heading"><h3 id="documents-heading">MOCK documents</h3>
    <p>A private confirmation summary for this simulation. It is not a ticket, voucher or tax invoice and cannot be used for travel.</p>
    {query.isPending&&<Loading label="Checking your documents…"/>}
    {query.isError&&<ErrorNotice error={query.error} retry={()=>query.refetch()}/>}
    {items?.length===0&&<><p>No summary has been generated yet.</p><button className="button secondary" disabled={create.isPending} onClick={()=>create.mutate()}>{create.isPending?"Preparing summary…":"Prepare MOCK summary"}</button></>}
    {create.isError&&<><ErrorNotice error={create.error}/><button className="button secondary" onClick={()=>query.refetch()}>Recover saved summary</button></>}
    {items?.map(d=><div key={d.id}><p>Issued {date(d.createdAt)} · {d.byteLength} bytes · private UTF-8 text</p>
      <button className="button secondary" disabled={download.isPending} onClick={()=>download.mutate(d.id)}>{download.isPending?"Downloading…":"Download MOCK summary"}</button>
    </div>)}
    {download.isError&&<ErrorNotice error={download.error}/>}
  </section>;
}
