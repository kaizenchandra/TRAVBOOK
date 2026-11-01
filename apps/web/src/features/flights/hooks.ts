"use client";
import { keepPreviousData, useMutation, useQuery } from "@tanstack/react-query";
import { api, unwrap, type SearchRequest } from "@/lib/api/client";
export function useLocations(query:string,enabled:boolean) {
  return useQuery({queryKey:["locations",query],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/locations",{params:{query:{q:query}},signal})),enabled,staleTime:300_000,retry:1});
}
export function useFlightSearch() {
  return useMutation({mutationFn:async(body:SearchRequest)=>unwrap(await api.POST("/api/v1/flights/search",{body})),retry:false});
}
export type Filters = {page:number;size:number;nonstop:boolean;sort:"PRICE"|"DURATION"|"DEPARTURE";airline:string};
export function useSearchPage(id:string|null,filters:Filters) {
  return useQuery({queryKey:["search",id,filters],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/flights/searches/{id}",{params:{path:{id:id!},query:filters},signal})),
    enabled:!!id,staleTime:60_000,retry:false,placeholderData:keepPreviousData});
}
export function useOffer(id:string) {
  return useQuery({queryKey:["offer",id],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/flights/offers/{id}",{params:{path:{id}},signal})),retry:false,staleTime:0});
}
export function useReprice(id:string) {
  return useMutation({mutationFn:async()=>unwrap(await api.POST("/api/v1/flights/offers/{id}/reprice",{params:{path:{id}}})),retry:false});
}

