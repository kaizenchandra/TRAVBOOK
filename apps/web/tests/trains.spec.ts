import{test,expect}from'@playwright/test';
import AxeBuilder from'@axe-core/playwright';
import Ajv2020 from'ajv/dist/2020.js';
import addFormats from'ajv-formats';
import contract from'../../../contracts/openapi.json' with{type:'json'};
import{travelDate}from'../src/lib/format';
const ajv=new Ajv2020({strict:false,allErrors:true});addFormats(ajv);
function schema(name:string,data:unknown){const validate=ajv.compile({$ref:'#/components/schemas/'+name,components:contract.components});expect(validate(data),JSON.stringify(validate.errors)).toBe(true);}
test('AC-042/043 — keyboard stations, search, class matrix and preserved filters',async({page},info)=>{
 await page.goto('/');await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Trains',exact:true}).click();
 const origin=page.getByRole('combobox',{name:'From station',exact:true});await origin.fill('Delhi');await expect(page.getByRole('option',{name:/New Delhi.*NDLS/})).toBeVisible();await origin.press('ArrowDown');await origin.press('Enter');await expect(origin).toHaveValue('NDLS');
 await page.getByRole('button',{name:'Swap stations'}).click();await expect(origin).toHaveValue('MMCT');await page.getByRole('button',{name:'Swap stations'}).click();
 const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/trains/search')&&r.request().method()==='POST');await page.getByRole('button',{name:'Search trains',exact:true}).click();const data=await(await response).json();schema('TrainSearchPage',data);
 await expect(page.getByText(/matching trains$/)).toBeVisible();await expect(page.getByText('RAC 8 · berth not guaranteed',{exact:true}).first()).toBeVisible();await expect(page.getByText('Waitlist 18 · not confirmed',{exact:true}).first()).toBeVisible();
 await page.getByRole('combobox',{name:'Travel class',exact:true}).selectOption('THREE_AC');await page.getByRole('combobox',{name:'Availability status',exact:true}).selectOption('WAITLIST');await expect(page.getByRole('heading',{name:'No matching sample trains'})).toBeVisible();
 await page.reload();await expect(page.getByRole('combobox',{name:'Travel class',exact:true})).toHaveValue('THREE_AC');await expect(origin).toHaveValue('NDLS');
 await page.getByRole('button',{name:'Reset filters'}).click();await expect(page.getByRole('link',{name:/Sleeper \(SL\)/}).first()).toBeVisible();
 expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('train-search-matrix.png'),fullPage:true});
});
test('AC-043/044 — fresh class/quota status, fare change and refresh never imply ticketing',async({page},info)=>{
 await page.goto('/trains');await page.getByRole('button',{name:'Search trains',exact:true}).click();await page.getByRole('link',{name:/Sleeper \(SL\)/}).first().click();
 const current=page.getByRole('complementary',{name:'Current availability'});const response=page.waitForResponse(r=>r.url().includes('/availability?')&&r.request().method()==='GET');await page.getByRole('button',{name:'Check current availability'}).click();const result=await(await response).json();schema('TrainAvailabilityCheck',result);expect(result.ticketingEnabled).toBe(false);
 await expect(current.getByText('RAC 3 · berth not guaranteed',{exact:true})).toBeVisible();await expect(current.getByText('Availability or fare changed.',{exact:true})).toBeVisible();
 await page.getByRole('combobox',{name:'Journey class',exact:true}).selectOption('TWO_AC');await expect(current.getByText('RAC 3 · berth not guaranteed',{exact:true})).not.toBeVisible();await page.getByRole('button',{name:'Check current availability'}).click();await expect(current.getByText('Waitlist 18 · not confirmed',{exact:true})).toBeVisible();
 await page.getByRole('combobox',{name:'Journey quota',exact:true}).selectOption('TATKAL');await page.reload();await expect(page.getByRole('combobox',{name:'Journey quota',exact:true})).toHaveValue('TATKAL');await expect(page.getByRole('combobox',{name:'Journey class',exact:true})).toHaveValue('TWO_AC');await page.getByRole('button',{name:'Check current availability'}).click();await expect(current.getByText('Quota not open',{exact:true})).toBeVisible();await expect(current.getByText(/This is not an official booking rule/)).toBeVisible();
 await expect(page.getByText('Real railway ticketing is not enabled.',{exact:true})).toBeVisible();expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('train-availability-review.png'),fullPage:true});
 await page.route(/\/api\/v1\/trains\/[^/]+\/availability\?/,async route=>{const response=await route.fetch();const body=await response.json();await route.fulfill({response,json:{...body,current:{...body.current,validUntil:'2000-01-01T00:00:00Z'}}});});await page.getByRole('button',{name:'Check current availability'}).click();await expect(current.getByText('This availability check expired. Check again for current status and fare.',{exact:true})).toBeVisible();await expect(current.getByText('Quota not open',{exact:true})).not.toBeVisible();
});
test('AC-045 — partial supplier, empty and failed searches remain distinguishable',async({page})=>{
 await page.goto('/trains');const destination=page.getByRole('combobox',{name:'To station',exact:true});
 for(const [text,code]of[['Madgaon','MAO'],['Jaipur','JP'],['Howrah','HWH']]){await destination.fill(text);await page.getByRole('option',{name:new RegExp(text+'.*'+code)}).click();await page.getByRole('button',{name:'Search trains',exact:true}).click();if(code==='MAO')await expect(page.getByText(/Some railway providers are unavailable/)).toBeVisible();if(code==='JP')await expect(page.getByRole('heading',{name:'No matching sample trains'})).toBeVisible();if(code==='HWH'){await expect(page.getByRole('main').getByRole('alert')).toContainText('Railway providers are unavailable');await expect(page.getByRole('region',{name:'Train results'})).not.toBeVisible();}}
});
test('AC-042/044 — station and journey schemas, invalid class, no PNR endpoint',async({page})=>{
 const stations=await page.request.get('/api/v1/railway-stations?q=mumbai');expect(stations.status()).toBe(200);schema('RailStation',(await stations.json())[0]);
 const searched=await page.request.post('/api/v1/trains/search',{data:{origin:'NDLS',destination:'MMCT',departureDate:travelDate(2),quota:'GENERAL'}});expect(searched.status()).toBe(200);const s=await searched.json();schema('TrainSearchPage',s);const id=s.items[0].id;
 const details=await page.request.get('/api/v1/trains/'+id);expect(details.status()).toBe(200);schema('TrainDetails',await details.json());
 expect((await page.request.get(`/api/v1/trains/${id}/availability?travelClass=INVALID&quota=GENERAL`)).status()).toBe(400);expect((await page.request.get('/api/v1/trains/pnr/1234567890')).status()).toBe(401);
 const bad=await page.request.post('/api/v1/trains/search',{data:{origin:'NDLS',destination:'NDLS',departureDate:travelDate(2),quota:'GENERAL'}});expect(bad.status()).toBe(422);schema('Problem',await bad.json());
});
