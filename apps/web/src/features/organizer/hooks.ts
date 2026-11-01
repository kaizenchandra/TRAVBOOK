"use client";
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {api,unwrap} from '@/lib/api/client';
import {csrfHeaders} from '@/features/account/hooks';
import {useOwnedGuard} from '@/features/account/OwnedArea';
import type {components} from '@/lib/api/schema';
export type Organizer=components['schemas']['TravelOrganizer'];
export type Fields=components['schemas']['OrganizerFields'];
export function useOrganizer(owner:string){const guard=useOwnedGuard(owner);return useQuery({queryKey:['organizer',owner],queryFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/me/travel-organizer')),v=>v.customerId),gcTime:0,retry:false,staleTime:0});}
export function useSaveOrganizer(owner:string){const guard=useOwnedGuard(owner),cache=useQueryClient();return useMutation({retry:false,mutationFn:({version,fields,key}:{version:number;fields:Fields;key:string})=>guard(async()=>unwrap(await api.PUT('/api/v1/me/travel-organizer',{headers:await csrfHeaders(),params:{header:{'Idempotency-Key':key}},body:{expectedCustomerId:owner,expectedVersion:version,fields,consent:true}})),v=>v.organizer.customerId),onSuccess:r=>cache.setQueryData(['organizer',owner],r.organizer)});}
export function useExportOrganizer(owner:string){const guard=useOwnedGuard(owner);return useMutation({retry:false,mutationFn:()=>guard(async()=>unwrap(await api.GET('/api/v1/me/travel-organizer')),v=>v.customerId)});}
