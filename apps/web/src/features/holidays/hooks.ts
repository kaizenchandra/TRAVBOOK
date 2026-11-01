"use client";
import {useMutation,useQuery,useQueryClient} from "@tanstack/react-query";
import {api,unwrap} from "@/lib/api/client";
import {csrfHeaders} from "@/features/account/hooks";
import type {components,paths} from "@/lib/api/schema";
export type HolidayFilters=NonNullable<paths["/api/v1/holidays/packages"]["get"]["parameters"]["query"]>;
export type HolidayQuoteRequest=components["schemas"]["HolidayQuoteRequest"];
export function useHolidays(query:HolidayFilters){return useQuery({queryKey:["holidays",query],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/holidays/packages",{params:{query},signal})),retry:false,staleTime:0});}
export function useHoliday(id:string){return useQuery({queryKey:["holiday",id],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/holidays/packages/{id}",{params:{path:{id}},signal})),retry:false,staleTime:0});}
export function useHolidayQuoteCreate(){return useMutation({mutationFn:async({body,key}:{body:HolidayQuoteRequest;key:string})=>unwrap(await api.POST("/api/v1/holidays/quotes",{body,params:{header:{"Idempotency-Key":key}},headers:await csrfHeaders()})),retry:false});}
export function useHolidayQuote(id:string,owner:string){return useQuery({queryKey:["holiday-quote",owner,id],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/holidays/quotes/{id}",{params:{path:{id}},signal})),retry:false,staleTime:0});}
export function useHolidayEnquiryCreate(){const c=useQueryClient();return useMutation({mutationFn:async(body:components["schemas"]["HolidayEnquiryRequest"])=>unwrap(await api.POST("/api/v1/holidays/enquiries",{body,params:{header:{"Idempotency-Key":"holiday-enquiry-"+body.quoteId}},headers:await csrfHeaders()})),retry:false,onSettled:()=>c.invalidateQueries({queryKey:["holiday-quote"]})});}
export function useHolidayEnquiry(id:string,owner:string){return useQuery({queryKey:["holiday-enquiry",owner,id],queryFn:async({signal})=>unwrap(await api.GET("/api/v1/holidays/enquiries/{id}",{params:{path:{id}},signal})),retry:false,staleTime:0});}
