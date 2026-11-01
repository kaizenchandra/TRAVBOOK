import { describe,it,expect } from "vitest";
import { unwrap,ApiError } from "./client";
describe("API failure semantics",()=>{
  it("keeps server problem codes and correlation IDs",()=>{
    try {unwrap({response:new Response(null,{status:409}),error:{code:"OFFER_EXPIRED",detail:"Search again",correlationId:"test-id"}});throw new Error("Expected failure");}
    catch(e){expect(e).toBeInstanceOf(ApiError);expect((e as ApiError).code).toBe("OFFER_EXPIRED");expect((e as ApiError).correlationId).toBe("test-id");}
  });
  it("does not turn an empty success into fabricated data",()=>{expect(()=>unwrap({response:new Response(null,{status:200})})).toThrow("incomplete response");});
  it("preserves successful data",()=>{expect(unwrap({response:new Response(null,{status:200}),data:[]})).toEqual([]);});
});
