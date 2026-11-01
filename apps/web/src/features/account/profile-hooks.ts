"use client";
import {useMutation,useQuery,useQueryClient} from "@tanstack/react-query";
import {api,ApiError,unwrap} from "@/lib/api/client";
import type {components} from "@/lib/api/schema";
import {csrfHeaders} from "./hooks";

export type Profile=components["schemas"]["CustomerProfile"];
export type ProfileInput=components["schemas"]["CustomerProfileInput"];
export function useProfile(owner:string){
  return useQuery({queryKey:["customer-profile",owner],queryFn:async()=>{
    const profile=unwrap(await api.GET("/api/v1/me/profile"));
    if(profile.customerId!==owner)throw new ApiError("PROFILE_IDENTITY_CONFLICT","Your signed-in account changed. Reload the account page.");
    return profile;
  },retry:false,staleTime:0,gcTime:0,refetchOnWindowFocus:true});
}
export function useSaveProfile(owner:string){
  const client=useQueryClient();
  return useMutation({mutationKey:["customer-profile-save",owner],retry:false,
    mutationFn:async({body,key}:{body:ProfileInput;key:string})=>unwrap(await api.PUT("/api/v1/me/profile",{
      params:{header:{...await csrfHeaders(),"Idempotency-Key":key}},body
    })),
    onSuccess:saved=>{if(saved.profile.customerId!==owner)throw new ApiError("PROFILE_IDENTITY_CONFLICT","Your account changed. Reload this page.");client.setQueryData(["customer-profile",owner],saved.profile);}
  });
}
