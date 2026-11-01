"use client";
import {useMutation,useQuery,useQueryClient} from "@tanstack/react-query";
import {api,unwrap,ApiError} from "@/lib/api/client";
import {csrfHeaders} from "@/features/account/hooks";
export function useCreateQuote() {
  return useMutation({mutationFn:async({offerId,key}:{offerId:string;key:string})=>unwrap(await api.POST("/api/v1/quotes",{
    body:{offerId},params:{header:{"Idempotency-Key":key}},headers:await csrfHeaders()
  })),retry:false});
}
export function useQuote(id:string,customerId?:string) {
  return useQuery({queryKey:["quote",customerId,id],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/quotes/{id}",{params:{path:{id}},signal})),
    enabled:!!customerId,retry:false,staleTime:0,refetchOnWindowFocus:true});
}
export function useAcceptQuote(id:string,customerId?:string) {
  const client=useQueryClient();
  return useMutation({mutationFn:async({version,key}:{version:number;key:string})=>unwrap(await api.POST("/api/v1/quotes/{id}/acceptance",{
    params:{path:{id},header:{"Idempotency-Key":key}},body:{version},headers:await csrfHeaders()
  })),retry:false,onSuccess:()=>client.invalidateQueries({queryKey:["quote",customerId,id]}),
    onError:error=>{if(error instanceof ApiError&&error.code==="UNAUTHENTICATED")void client.invalidateQueries({queryKey:["session"]});}});
}

