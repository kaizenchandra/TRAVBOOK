"use client";
import {useQuery} from "@tanstack/react-query";
import {api,unwrap} from "@/lib/api/client";
export function useSession() {
  return useQuery({queryKey:["session"],queryFn:async()=>unwrap(await api.GET("/auth/session")),retry:false,staleTime:0,refetchOnWindowFocus:true});
}
export function useCustomer(enabled:boolean) {
  return useQuery({queryKey:["customer"],queryFn:async()=>unwrap(await api.GET("/api/v1/me")),enabled,retry:false,staleTime:0,refetchOnWindowFocus:true});
}
export async function csrfHeaders() {
  const session=unwrap(await api.GET("/auth/session"));
  return {[session.csrfHeaderName]:session.csrfToken};
}

