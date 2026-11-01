import createClient from "openapi-fetch";
import type { paths, components } from "./schema";
export const api = createClient<paths>({baseUrl:""});
export type SearchRequest = components["schemas"]["SearchRequest"];
export type Offer = components["schemas"]["Offer"];
export type SearchPage = components["schemas"]["SearchPage"];
export type Reprice = components["schemas"]["Reprice"];
export type Location = components["schemas"]["Location"];
export class ApiError extends Error {
  constructor(public code:string, message:string, public correlationId?:string) { super(message); }
}
export function unwrap<T>(result:{data?:T;error?:unknown;response:Response}):T {
  if(result.error || !result.response.ok) {
    const error=result.error as Partial<components["schemas"]["Problem"]> | undefined;
    throw new ApiError(error?.code ?? "NETWORK_ERROR",error?.detail ?? "Unable to reach the travel service. Please try again.",error?.correlationId);
  }
  if(result.data===undefined) throw new ApiError("EMPTY_RESPONSE","The service returned an incomplete response.");
  return result.data;
}

