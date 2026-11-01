"use client";
import {useMutation,useQuery} from "@tanstack/react-query";
import {api,unwrap} from "@/lib/api/client";
import type {components,paths} from "@/lib/api/schema";
export type BusSeat=components["schemas"]["BusSeat"];
export type BusCriteria=components["schemas"]["BusCriteria"];
export type BusFilter=NonNullable<paths["/api/v1/bus-searches/{id}"]["get"]["parameters"]["query"]>;
export function useBusSearch(){return useMutation({mutationFn:async(body:BusCriteria)=>unwrap(await api.POST("/api/v1/buses/search",{body})),retry:false});}
export function useBusPage(id:string|null,query:BusFilter){return useQuery({queryKey:["bus-search",id,query],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/bus-searches/{id}",{params:{path:{id:id!},query},signal})),enabled:!!id,retry:false,staleTime:0});}
export function useBusTrip(search:string|null,id:string){return useQuery({queryKey:["bus-trip",search,id],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/buses/trips/{id}",{params:{path:{id},query:{searchId:search!}},signal})),enabled:!!search,retry:false,staleTime:0});}
export function useBusSeats(){return useMutation({mutationFn:async({id,searchId}:{id:string;searchId:string})=>unwrap(await api.GET("/api/v1/buses/trips/{id}/seats",{params:{path:{id},query:{searchId}}})),retry:false});}
export function useBusFare(id:string,body:components["schemas"]["BusFareRequest"]){return useQuery({queryKey:["bus-fare",id,body],queryFn:async({signal})=>unwrap(await api.POST("/api/v1/buses/trips/{id}/reprice",{params:{path:{id}},body,signal})),enabled:body.seatIds.length>0&&!!body.searchId&&!!body.boardingId&&!!body.droppingId,retry:false,staleTime:0,refetchOnWindowFocus:false});}
