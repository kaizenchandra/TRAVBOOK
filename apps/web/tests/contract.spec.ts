import {test,expect} from "@playwright/test";
import Ajv2020 from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import contract from "../../../contracts/openapi.json" with {type:"json"};
import {travelDate} from "../src/lib/format";
const ajv=new Ajv2020({strict:false,allErrors:true});addFormats(ajv);
function matches(name:string,payload:unknown) {
  const validate=ajv.compile({$ref:"#/components/schemas/"+name,components:contract.components});
  expect(validate(payload),JSON.stringify(validate.errors)).toBe(true);
}
test("HTTP responses conform to the authoritative OpenAPI schemas",async({request})=>{
  const locations=await request.get("/api/v1/locations?q=Delhi");expect(locations.status()).toBe(200);
  for(const location of await locations.json())matches("Location",location);
  const location=await request.get("/api/v1/locations/DEL");matches("Location",await location.json());
  const search=await request.post("/api/v1/flights/search",{data:{origin:"DEL",destination:"BOM",departureDate:travelDate(2),returnDate:null,adults:1,children:0,infants:0,cabin:"ECONOMY"}});
  expect(search.status()).toBe(200);const page=await search.json();matches("SearchPage",page);
  const paged=await request.get("/api/v1/flights/searches/"+page.id+"?page=1&size=5");matches("SearchPage",await paged.json());
  const offer=await request.get("/api/v1/flights/offers/"+page.offers[0].id);matches("Offer",await offer.json());
  const reprice=await request.post("/api/v1/flights/offers/"+page.offers[0].id+"/reprice");matches("Reprice",await reprice.json());
  const invalid=await request.post("/api/v1/flights/search",{data:{}});expect(invalid.status()).toBe(400);matches("Problem",await invalid.json());
  expect(invalid.headers()["content-type"]).toContain("application/problem+json");expect(invalid.headers()["x-correlation-id"]).toBeTruthy();
});
