"use client";
import {useMutation,useQuery} from "@tanstack/react-query";
import {api,unwrap} from "@/lib/api/client";
import type {components,paths} from "@/lib/api/schema";
export type HotelSearchRequest=components["schemas"]["HotelSearchRequest"];
export type HotelOffer=components["schemas"]["HotelOffer"];
export type HotelFilters=NonNullable<paths["/api/v1/hotel-searches/{id}"]["get"]["parameters"]["query"]>;
export function useHotelSearch(){return useMutation({mutationFn:async(body:HotelSearchRequest)=>unwrap(await api.POST("/api/v1/hotels/search",{body})),retry:false});}
export function useHotelResults(id:string|null,filters:HotelFilters){return useQuery({queryKey:["hotels",id,filters],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/hotel-searches/{id}",{params:{path:{id:id!},query:filters},signal})),enabled:!!id,retry:false,staleTime:0});}
export function useHotelRooms(id:string,searchId:string|null){return useQuery({queryKey:["hotel-rooms",id,searchId],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/hotels/{id}/rooms",{params:{path:{id},query:{searchId:searchId!}},signal})),enabled:!!searchId,retry:false,staleTime:0});}
export function useHotelReprice(){return useMutation({mutationFn:async(offerIds:string[])=>unwrap(await api.POST("/api/v1/hotels/offers/{id}/reprice",{params:{path:{id:offerIds[0]}},body:{offerIds}})),retry:false});}
