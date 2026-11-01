"use client";
import {useMutation,useQuery,useInfiniteQuery,useQueryClient} from "@tanstack/react-query";
import {useRef} from "react";
import {api,unwrap,ApiError} from "@/lib/api/client";
import type {components} from "@/lib/api/schema";
import {csrfHeaders} from "@/features/account/hooks";
export type Traveller=components["schemas"]["Traveller"];
export function useBookingRequirements(quoteId:string,customerId?:string) {
  return useQuery({queryKey:["booking-requirements",customerId,quoteId],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/quotes/{id}/booking-requirements",{params:{path:{id:quoteId}},signal})),
    enabled:!!customerId,retry:false,staleTime:0,refetchOnWindowFocus:true});
}
export function useCreateBooking() {
  const client=useQueryClient();
  return useMutation({mutationFn:async({body,key}:{body:components["schemas"]["CreateBooking"];key:string})=>unwrap(await api.POST("/api/v1/bookings",{
    body,params:{header:{"Idempotency-Key":key}},headers:await csrfHeaders()
  })),retry:false,onSuccess:()=>client.invalidateQueries({queryKey:["bookings"]}),
    onError:error=>{if(error instanceof ApiError&&error.code==="UNAUTHENTICATED")void client.invalidateQueries({queryKey:["session"]});}});
}
export function useBooking(id:string,customerId?:string) {
  const budget=useRef({operation:"",firstRead:0});
  return useQuery({queryKey:["booking",customerId,id],queryFn:async({signal})=>unwrap(await api.GET("/api/v3/bookings/{id}",{params:{path:{id}},signal})),
    enabled:!!customerId,retry:false,staleTime:0,refetchOnWindowFocus:true,refetchIntervalInBackground:false,
    refetchInterval:query=>{
      const reads=query.state.dataUpdateCount+query.state.errorUpdateCount;
      const operation=id+":"+customerId+":"+(query.state.data?.cancellation?.id??query.state.data?.checkout?.id??"validation");
      if(budget.current.operation!==operation)budget.current={operation,firstRead:reads};
      if(query.state.error||(query.state.data?.cancellation?query.state.data.cancellation.terminal:query.state.data?.checkout?query.state.data.checkout.terminal:query.state.data?.workflow.terminal))return false;
      const phaseReads=reads-budget.current.firstRead;
      return phaseReads>=20?false:Math.min(1000*Math.pow(1.5,phaseReads),8000);
    }});
}
export function useBookings(customerId?:string) {
  return useInfiniteQuery({queryKey:["bookings",customerId],initialPageParam:undefined as string|undefined,
    queryFn:async({pageParam,signal})=>unwrap(await api.GET("/api/v3/me/bookings",{params:{query:{size:10,after:pageParam}},signal})),
    getNextPageParam:page=>page.nextCursor??undefined,enabled:!!customerId,retry:false,staleTime:0});
}
export function useStartCheckout(id:string) {
  const client=useQueryClient();
  return useMutation({mutationFn:async({scenario,key}:{scenario:components["schemas"]["StartCheckout"]["scenario"];key:string})=>unwrap(await api.POST("/api/v2/bookings/{id}/checkout",{params:{path:{id},header:{"Idempotency-Key":key}},body:{scenario,consent:true},headers:await csrfHeaders()})),
    retry:false,onSuccess:()=>{void client.invalidateQueries({queryKey:["booking"]});void client.invalidateQueries({queryKey:["bookings"]});},
    onError:error=>{if(error instanceof ApiError&&error.code==="UNAUTHENTICATED")void client.invalidateQueries({queryKey:["session"]});}});
}
export function useBookingTimeline(id:string,version:number,customerId?:string) {
  return useInfiniteQuery({queryKey:["booking-timeline",customerId,id,version],initialPageParam:0,
    queryFn:async({pageParam,signal})=>unwrap(await api.GET("/api/v1/bookings/{id}/timeline",{params:{path:{id},query:{afterVersion:pageParam,size:20}},signal})),
    getNextPageParam:page=>page.nextAfterVersion??undefined,enabled:!!customerId,retry:false,staleTime:0,
    refetchOnWindowFocus:true,refetchIntervalInBackground:false,
    refetchInterval:query=>{
      const latest=query.state.data?.pages.at(-1);
      if(query.state.error||latest?.deliveryState==="ATTENTION_REQUIRED"||
        (latest?.deliveryState==="CAUGHT_UP"&&latest.deliveredVersion>=version))return false;
      const reads=query.state.dataUpdateCount+query.state.errorUpdateCount;
      return reads>=20?false:Math.min(1000*Math.pow(1.5,reads),8000);
    }});
}

