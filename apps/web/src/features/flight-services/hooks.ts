"use client";
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api,unwrap} from '@/lib/api/client';
import type {components} from '@/lib/api/schema';
import {csrfHeaders} from '@/features/account/hooks';
import {useOwnedGuard} from '@/features/account/OwnedArea';
type S=components['schemas'];
export type Selection=S['FpSelection'];export type Reservation=S['FpReservation'];export type Quote=S['FpQuote'];
export function useSeats(date:string){return useQuery({queryKey:['fp-seats',date],queryFn:async()=>unwrap(await api.GET('/api/v1/demo/flight-provider/flights/{flightId}/seats',{params:{path:{flightId:'MOCK-FA-'+date}}})).data,retry:false,staleTime:0});}
export function useAncillaries(date:string){return useQuery({queryKey:['fp-ancillaries',date],queryFn:async()=>unwrap(await api.GET('/api/v1/demo/flight-provider/flights/{flightId}/ancillaries',{params:{path:{flightId:'MOCK-FA-'+date}}})).data,retry:false,staleTime:0});}
export function useQuote(owner:string,id:string|null){const guard=useOwnedGuard(owner);return useQuery({queryKey:['fp-quote',owner,id],enabled:!!id,gcTime:0,retry:false,queryFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/demo/flight-provider/quotes/{id}',{params:{path:{id:id!}}})).data,v=>v.owner)});}
export function useCreateQuote(owner:string){const guard=useOwnedGuard(owner);return useMutation({retry:false,mutationFn:({body,key}:{body:S['FpQuoteInput']&{couponCode:string};key:string})=>guard(async()=>unwrap(await api.POST('/api/v2/demo/flight-provider/quotes',{body:{...body,expectedCustomerId:owner},params:{header:{'Idempotency-Key':key}},headers:await csrfHeaders()})).data,v=>v.owner)});}
export function useReserve(owner:string){const guard=useOwnedGuard(owner);return useMutation({retry:false,mutationFn:(quote:Quote)=>guard(async()=>unwrap(await api.POST('/api/v1/demo/flight-provider/reservations',{body:{quoteId:quote.id,quoteVersion:1,acceptedTotal:quote.total,consent:true},params:{header:{'Idempotency-Key':'fp-reserve-'+quote.id}},headers:await csrfHeaders()})).data,v=>v.owner)});}
export function useReservation(owner:string,id:string){const guard=useOwnedGuard(owner);return useQuery({queryKey:['fp-reservation',owner,id],gcTime:0,retry:false,refetchOnWindowFocus:true,queryFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/demo/flight-provider/reservations/{id}',{params:{path:{id}}})).data,v=>v.owner)});}
export function useReservations(owner:string,after:string|null){const guard=useOwnedGuard(owner);return useQuery({queryKey:['fp-history',owner,after],gcTime:0,retry:false,queryFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/demo/flight-provider/reservations',{params:{query:after?{after}:{}}})).data,v=>v.owner)});}
export type Action={kind:'payment';outcome:'SUCCEED'|'DECLINE'}|{kind:'ticket';outcome:'ISSUE'|'REJECT'}|{kind:'check-in';passengerIds:string[]}|{kind:'cancel'|'refund'};
export function useAction(owner:string,id:string){const guard=useOwnedGuard(owner),cache=useQueryClient();return useMutation({retry:false,mutationFn:async({action,version}:{action:Action;version:number})=>guard(async()=>{
 const key=`fp-${id}-${version}-${action.kind}-${'outcome'in action?action.outcome:'passengerIds'in action?action.passengerIds.slice().sort().join('-'):'all'}`;
 const common={params:{path:{id},header:{'Idempotency-Key':key}},headers:await csrfHeaders()};
 switch(action.kind){
 case 'payment':return unwrap(await api.POST('/api/v1/demo/flight-provider/reservations/{id}/payments',{...common,body:{outcome:action.outcome,consent:true}})).data;
 case 'ticket':return unwrap(await api.POST('/api/v1/demo/flight-provider/reservations/{id}/tickets',{...common,body:{outcome:action.outcome,consent:true}})).data;
 case 'check-in':return unwrap(await api.POST('/api/v1/demo/flight-provider/reservations/{id}/check-in',{...common,body:{passengerIds:action.passengerIds,consent:true}})).data;
 case 'cancel':return unwrap(await api.POST('/api/v1/demo/flight-provider/reservations/{id}/cancellations',{...common,body:{consent:true}})).data;
 case 'refund':return unwrap(await api.POST('/api/v1/demo/flight-provider/reservations/{id}/refunds',{...common,body:{consent:true}})).data;
 }
 },v=>v.owner),onSuccess:()=>{cache.invalidateQueries({queryKey:['fp-reservation',owner,id]});cache.invalidateQueries({queryKey:['fp-history',owner]});}});}
export function useDocuments(owner:string,id:string){const guard=useOwnedGuard(owner);return useMutation({retry:false,mutationFn:async()=>{const result=await guard(async()=>{const reservation=unwrap(await api.GET('/api/v1/demo/flight-provider/reservations/{id}',{params:{path:{id}}})).data;const documents=unwrap(await api.GET('/api/v1/demo/flight-provider/reservations/{id}/documents',{params:{path:{id}}})).data;return {owner:reservation.owner,documents};},v=>v.owner);return result.documents;}});}

export function useCoupons(owner:string){const guard=useOwnedGuard(owner);return useQuery({queryKey:['coupons',owner],gcTime:0,retry:false,queryFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/coupons')),v=>v.customerId)});}
export function usePromotion(owner:string,id:string){const guard=useOwnedGuard(owner);return useQuery({queryKey:['flight-promotion',owner,id],gcTime:0,retry:false,queryFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/demo/flight-provider/quotes/{id}/promotion',{params:{path:{id}}})),v=>v.owner)});}
