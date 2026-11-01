"use client";
import {createContext,useContext,useState,type ReactNode} from "react";
import {useSession,useCustomer} from "./hooks";
import {LoginPanel} from "./Account";
import {Loading,ErrorNotice} from "@/components/Feedback";
import {ApiError} from "@/lib/api/client";
const AccessFailure=createContext<(e:Error)=>void>(()=>{});
export function useOwnedGuard(owner:string){const fail=useContext(AccessFailure);return async<T,>(request:()=>Promise<T>,identity:(v:T)=>string)=>{try{const value=await request();if(identity(value)!==owner)throw new ApiError("PROFILE_IDENTITY_CONFLICT","Your signed-in account changed. Reload this page.");return value;}catch(e){if(e instanceof ApiError&&['UNAUTHENTICATED','FORBIDDEN','PROFILE_IDENTITY_CONFLICT','NOT_FOUND'].includes(e.code))fail(e);throw e;}};}
function Boundary({children}:{children:ReactNode}){const [error,setError]=useState<Error|null>(null);return error?<ErrorNotice error={error} retry={()=>location.reload()}/>:<AccessFailure.Provider value={setError}>{children}</AccessFailure.Provider>;}
export function OwnedArea({children,returnTo}:{children:(owner:string)=>ReactNode;returnTo:string}){
 const session=useSession(),customer=useCustomer(session.data?.authenticated===true);
 if(session.isPending)return <Loading label="Checking your session…"/>;
 if(session.isError)return <ErrorNotice error={session.error} retry={()=>session.refetch()}/>;
 if(!session.data.authenticated)return <LoginPanel enabled={session.data.loginEnabled} local={session.data.identityMode==='LOCAL_OIDC'} stay={returnTo}/>;
 if(customer.isPending)return <Loading label="Loading your account…"/>;
 if(customer.isError)return <ErrorNotice error={customer.error} retry={()=>customer.refetch()}/>;
 return customer.data?<Boundary key={customer.data.id}>{children(customer.data.id)}</Boundary>:null;
}
