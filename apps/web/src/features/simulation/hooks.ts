"use client";
import {createContext,useContext} from "react";
import {useMutation,useQuery,useQueryClient} from "@tanstack/react-query";
import {api,ApiError,unwrap} from "@/lib/api/client";
import {csrfHeaders} from "@/features/account/hooks";
import type {components} from "@/lib/api/schema";
export type OperationInput=components["schemas"]["DemoOperationInput"];
export const PrivacyFailure=createContext<(error:Error)=>void>(()=>{});
function useGuard(owner:string){const deny=useContext(PrivacyFailure);return async<T,>(request:()=>Promise<T>,ownerOf:(value:T)=>string=(v)=>(v as {customerId:string}).customerId)=>{
  try{const value=await request();if(ownerOf(value)!==owner)throw new ApiError("PROFILE_IDENTITY_CONFLICT","Your signed-in account changed. Reload the sandbox.");return value;}
  catch(error){if(error instanceof ApiError&&['UNAUTHENTICATED','FORBIDDEN','PROFILE_IDENTITY_CONFLICT'].includes(error.code))deny(error);throw error;}
};}
export function useCatalog(){return useQuery({queryKey:['demo-catalog'],queryFn:async()=>unwrap(await api.GET('/api/v1/demo/catalog')),retry:false});}
export function useDeployment(){return useQuery({queryKey:['demo-deployment'],queryFn:async()=>unwrap(await api.GET('/api/v1/demo/deployment')),retry:false});}
export function useDemoOperations(owner:string,after:string|null){const guard=useGuard(owner);return useQuery({queryKey:['demo-operations',owner,after],queryFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/demo/operations',{params:{query:after?{after}:{}}}))),retry:false,gcTime:0,staleTime:0,refetchOnWindowFocus:true});}
export function useDemoOperation(owner:string,id:string|null){const guard=useGuard(owner);return useQuery({queryKey:['demo-operation',owner,id],enabled:!!id,queryFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/demo/operations/{id}',{params:{path:{id:id!}}}))),retry:false,gcTime:0,refetchIntervalInBackground:false,
  refetchInterval:q=>!q.state.error&&q.state.data&&!q.state.data.terminal&&q.state.dataUpdateCount<30?Math.min(10000,3000+q.state.dataUpdateCount*250):false});}
export function useCreateDemo(owner:string){const guard=useGuard(owner),cache=useQueryClient();return useMutation({mutationFn:({body,key}:{body:OperationInput;key:string})=>guard(async()=>unwrap(await api.POST('/api/v1/demo/operations',{params:{header:{'Idempotency-Key':key}},headers:await csrfHeaders(),body}))),retry:false,onSuccess:()=>cache.invalidateQueries({queryKey:['demo-operations',owner]})});}
export function useCouponPreview(owner:string){const guard=useGuard(owner);return useMutation({mutationFn:(body:components['schemas']['DemoCouponInput'])=>guard(async()=>unwrap(await api.POST('/api/v1/demo/coupons/preview',{body,headers:await csrfHeaders()}))),retry:false});}
export function usePrivacyPreview(owner:string){const guard=useGuard(owner);return useMutation({mutationFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/demo/privacy-preview')),v=>v.profile.customerId),retry:false});}
export function useFinancialReport(owner:string){const guard=useGuard(owner);return useMutation({mutationFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/demo/financial-report'))),retry:false});}
export function useDemoPayment(owner:string){const guard=useGuard(owner);return useMutation({mutationFn:(id:string)=>guard(async()=>unwrap(await api.GET('/api/v1/demo/payments/{id}',{params:{path:{id}}}))),retry:false});}
export function useDemoGps(owner:string,id:string|null){const guard=useGuard(owner);return useQuery({queryKey:['demo-gps',owner,id],enabled:!!id,queryFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/demo/gps/{id}',{params:{path:{id:id!}}}))),retry:false,gcTime:0,staleTime:0,refetchIntervalInBackground:false,
  refetchInterval:q=>!q.state.error&&q.state.data&&!q.state.data.terminal&&q.state.dataUpdateCount<30?10000:false});}
export function useAdvanceGps(id:string|null){const cache=useQueryClient();return useMutation({mutationFn:async(version:number)=>unwrap(await api.POST('/api/v2/journey-bookings/{id}/dispatch-demo',{params:{path:{id:id!},header:{'Idempotency-Key':`gps-${id}-${version}`}},headers:await csrfHeaders(),body:{version,consent:true}})),retry:false,onSuccess:()=>cache.invalidateQueries({queryKey:['demo-gps']})});}
