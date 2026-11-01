"use client";
import{useQuery,useMutation,useQueryClient}from"@tanstack/react-query";
import{api,unwrap}from"@/lib/api/client";
import{csrfHeaders}from"@/features/account/hooks";
import type{components,paths}from"@/lib/api/schema";
export type Review=components["schemas"]["ReviewCase"];
type LegacyKind=paths["/api/v1/operations/reviews/{kind}"]["get"]["parameters"]["path"]["kind"];
type JourneyKind=paths["/api/v2/operations/reviews/{kind}"]["get"]["parameters"]["path"]["kind"];
export type Kind=LegacyKind|JourneyKind;
const journey=(kind:Kind):kind is JourneyKind=>kind==="BUS"||kind==="HOLIDAY"||kind==="CAB";
export type Reason=components["schemas"]["ReviewResume"]["reason"];
export function useReviews(kind:Kind,page:number){return useQuery({queryKey:["operations-reviews",kind,page],queryFn:async({signal})=>journey(kind)?unwrap(await api.GET("/api/v2/operations/reviews/{kind}",{params:{path:{kind},query:{page}},signal})):unwrap(await api.GET("/api/v1/operations/reviews/{kind}",{params:{path:{kind},query:{page}},signal})),retry:false,staleTime:0});}
export function useReview(kind:Kind,id:string){return useQuery({queryKey:["operations-review",kind,id],queryFn:async({signal})=>journey(kind)?unwrap(await api.GET("/api/v2/operations/reviews/{kind}/{id}",{params:{path:{kind,id}},signal})):unwrap(await api.GET("/api/v1/operations/reviews/{kind}/{id}",{params:{path:{kind,id}},signal})),enabled:!!id,retry:false,staleTime:0});}
export function useResumeReview(kind:Kind,id:string){const client=useQueryClient();return useMutation({mutationFn:async({version,reason,key}:{version:number;reason:Reason;key:string})=>{const headers=await csrfHeaders();return journey(kind)?unwrap(await api.POST("/api/v2/operations/reviews/{kind}/{id}/resume",{params:{path:{kind,id},header:{"Idempotency-Key":key}},headers,body:{version,reason,consent:true}})):unwrap(await api.POST("/api/v1/operations/reviews/{kind}/{id}/resume",{params:{path:{kind,id},header:{"Idempotency-Key":key}},headers,body:{version,reason,consent:true}}));},retry:false,onSettled:()=>{void client.invalidateQueries({queryKey:["operations-review",kind,id]});void client.invalidateQueries({queryKey:["operations-reviews",kind]});}});}
export function useLegacyReview(kind:Kind,id:string){const client=useQueryClient();return useMutation({mutationFn:async({version,key}:{version:number;key:string})=>{if(journey(kind))throw new Error("Journey legacy correction is not supported.");return unwrap(await api.POST("/api/v1/operations/reviews/{kind}/{id}/reconcile-legacy",{params:{path:{kind,id},header:{"Idempotency-Key":key}},headers:await csrfHeaders(),body:{version,reason:"PROVIDER_STATUS_AVAILABLE",consent:true}}));},retry:false,onSettled:()=>{void client.invalidateQueries({queryKey:["operations-review",kind,id]});void client.invalidateQueries({queryKey:["operations-reviews",kind]});}});}
