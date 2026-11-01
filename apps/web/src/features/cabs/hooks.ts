"use client";
import {useMutation,useQuery} from "@tanstack/react-query";
import {api,unwrap} from "@/lib/api/client";
import type {components,paths} from "@/lib/api/schema";
export type CabCriteria=components["schemas"]["CabCriteria"];
export type CabFilters=NonNullable<paths["/api/v1/cab-searches/{id}"]["get"]["parameters"]["query"]>;
export function useCabSearch(){return useMutation({mutationFn:async(body:CabCriteria)=>unwrap(await api.POST("/api/v1/cabs/search",{body})),retry:false});}
export function useCabPage(id:string|null,query:CabFilters){return useQuery({queryKey:["cab-search",id,query],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/cab-searches/{id}",{params:{path:{id:id!},query},signal})),enabled:!!id,retry:false,staleTime:0});}
export function useCabDetail(id:string,search:string|null){return useQuery({queryKey:["cab-detail",id,search],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/cabs/offers/{id}",{params:{path:{id},query:{searchId:search!}},signal})),enabled:!!search,retry:false,staleTime:0});}
export function useCabReprice(id:string,searchId:string){return useMutation({mutationFn:async()=>unwrap(await api.POST("/api/v1/cabs/offers/{id}/reprice",{params:{path:{id}},body:{searchId}})),retry:false});}
