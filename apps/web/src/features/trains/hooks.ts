"use client";
import{useMutation,useQuery}from"@tanstack/react-query";
import{api,unwrap}from"@/lib/api/client";
import type{components,paths}from"@/lib/api/schema";
export type RailSearch=components["schemas"]["TrainSearchRequest"];
export type Availability=components["schemas"]["RailAvailability"];
export type RailFilters=NonNullable<paths["/api/v1/train-searches/{id}"]["get"]["parameters"]["query"]>;
export const classNames={SL:"Sleeper (SL)",THREE_AC:"AC 3 tier (3A)",TWO_AC:"AC 2 tier (2A)"};
export function statusLabel(a:Availability){switch(a.status){case"AVAILABLE":return `Available · ${a.position}`;case"RAC":return `RAC ${a.position} · berth not guaranteed`;case"WAITLIST":return `Waitlist ${a.position} · not confirmed`;case"UNAVAILABLE":return"Unavailable";case"NOT_OPEN":return"Quota not open";}}
export function useStations(q:string,enabled:boolean){return useQuery({queryKey:["rail-stations",q],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/railway-stations",{params:{query:{q}},signal})),enabled,staleTime:300_000,retry:1});}
export function useTrainSearch(){return useMutation({mutationFn:async(body:RailSearch)=>unwrap(await api.POST("/api/v1/trains/search",{body})),retry:false});}
export function useTrainResults(id:string|null,filters:RailFilters){return useQuery({queryKey:["train-search",id,filters],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/train-searches/{id}",{params:{path:{id:id!},query:filters},signal})),enabled:!!id,retry:false,staleTime:0});}
export function useJourney(id:string){return useQuery({queryKey:["train-journey",id],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/trains/{id}",{params:{path:{id}},signal})),retry:false,staleTime:0});}
export function useRailAvailability(id:string){return useMutation({mutationFn:async(selection:{travelClass:Availability["travelClass"];quota:Availability["quota"]})=>unwrap(await api.GET("/api/v1/trains/{id}/availability",{params:{path:{id},query:selection}})),retry:false});}
