"use client";
import { ApiError } from "@/lib/api/client";
export function ErrorNotice({error,retry}:{error:Error;retry?:()=>void}) {
  return <div className="notice error" role="alert"><strong>We couldn’t complete that request.</strong><p>{error.message}</p>
    {error instanceof ApiError && error.correlationId && <small>Support reference: {error.correlationId}</small>}
    {retry && <button className="button secondary" onClick={retry}>Try again</button>}
  </div>;
}
export function Loading({label="Finding your next journey…"}:{label?:string}) {
  return <div role="status" className="loading"><span className="spinner" aria-hidden="true"/>{label}<div className="skeleton"/><div className="skeleton"/></div>;
}

