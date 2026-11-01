import {test,expect,type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import contract from '../../../contracts/openapi.json' with {type:'json'};
import {travelDate} from '../src/lib/format';
// Read-only discovery fixtures use a window separate from retained booking/stock tests.
async function hotelDates(page:Page){await page.getByLabel('Check-in',{exact:true}).fill(travelDate(300));await page.getByLabel('Check-out',{exact:true}).fill(travelDate(302));}
const ajv=new Ajv2020({strict:false,allErrors:true});addFormats(ajv);
function schema(name:string,value:unknown){const validate=ajv.compile({$ref:'#/components/schemas/'+name,components:contract.components});expect(validate(value),JSON.stringify(validate.errors)).toBe(true);}
test('AC-038/039 — hotel search, canonical filters, illustrations and room comparison',async({page},info)=>{
  await page.goto('/');await page.getByRole('navigation',{name:'Primary'}).getByRole('link',{name:'Hotels',exact:true}).click();
  await expect(page.getByRole('heading',{name:/Find your next hotel/})).toBeVisible();await hotelDates(page);
  await page.getByRole('combobox',{name:'Rooms',exact:true}).selectOption('2');
  await page.getByRole('combobox',{name:'Children in room 2 Age 0–17 at check-in',exact:true}).selectOption('1');await page.getByLabel('Room 2, child 1 age',{exact:true}).fill('12');
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/hotels/search')&&r.request().method()==='POST');
  await page.getByRole('button',{name:'Search hotels',exact:true}).click();schema('HotelSearchPage',await(await response).json());
  await expect(page.getByText('4 matching properties',{exact:true})).toBeVisible();
  await page.getByRole('combobox',{name:'Property type',exact:true}).selectOption('HOTEL');await page.getByRole('combobox',{name:'Minimum stars',exact:true}).selectOption('5');
  await expect(page.getByText('1 matching properties',{exact:true})).toBeVisible();await page.reload();
  await expect(page.getByLabel('Room 2, child 1 age',{exact:true})).toHaveValue('12');await expect(page.getByRole('combobox',{name:'Property type',exact:true})).toHaveValue('HOTEL');
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
  await page.getByRole('link',{name:'Compare rooms'}).click();await expect(page.getByRole('heading',{name:'Lotus Garden Hotel',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'View illustration 2'}).click();await expect(page.getByRole('button',{name:'View illustration 2'})).toHaveAttribute('aria-pressed','true');
  const first=page.getByRole('group',{name:'Room 1 · 2 adult(s)',exact:true});const second=page.getByRole('group',{name:'Room 2 · 2 adult(s) · Children aged 12',exact:true});
  await first.getByRole('radio',{name:'Standard double · FLEX',exact:true}).focus();await page.keyboard.press('Space');await expect(first.getByRole('radio',{name:'Standard double · FLEX',exact:true})).toBeChecked();await second.getByRole('radio',{name:'Family room · FAMILY',exact:true}).check();
  await page.reload();await expect(first.getByRole('radio',{name:'Standard double · FLEX',exact:true})).toBeChecked();
  const reprice=page.waitForResponse(r=>r.url().endsWith('/reprice')&&r.request().method()==='POST');await page.getByRole('button',{name:'Recheck selected rooms'}).click();
  const repriced=await(await reprice).json();schema('HotelReprice',repriced);expect(repriced.reservationCreated).toBe(false);expect(repriced.offers).toHaveLength(2);
  const review=page.getByRole('region',{name:'Revalidated stay'});await expect(review.getByRole('heading',{name:'The price or policy changed'})).toBeVisible();
  await expect(review.getByText('Rates reviewed for this selection. This is not an accepted quotation or booking.')).not.toBeVisible();
  await review.getByRole('checkbox',{name:'I have reviewed the updated total and room policies.'}).check();await expect(review.getByText('Rates reviewed for this selection. This is not an accepted quotation or booking.')).toBeVisible();
  await expect(page.getByRole('link',{name:'Get an owned hotel quote',exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
  await page.screenshot({path:info.outputPath('hotel-room-comparison.png'),fullPage:true});
  await page.getByRole('link',{name:'← Back to hotel results'}).click();await expect(page.getByRole('combobox',{name:'Minimum stars',exact:true})).toHaveValue('5');
});
test('AC-040 — unavailable room and changed selection clear prior rate acceptance',async({page})=>{
  await page.goto('/hotels');await hotelDates(page);await page.getByRole('button',{name:'Search hotels',exact:true}).click();await page.getByRole('link',{name:'Compare rooms'}).first().click();
  await page.getByRole('radio',{name:'Family room · FAMILY',exact:true}).check();await page.getByRole('button',{name:'Recheck selected rooms'}).click();
  await page.getByRole('checkbox',{name:'I have reviewed the updated total and room policies.'}).check();
  await page.getByRole('radio',{name:'Studio · STUDIO',exact:true}).check();await expect(page.getByRole('region',{name:'Revalidated stay'})).not.toBeVisible();
  await page.getByRole('button',{name:'Recheck selected rooms'}).click();await expect(page.getByRole('main').getByRole('alert')).toContainText('no longer available');await expect(page.getByRole('region',{name:'Revalidated stay'})).not.toBeVisible();
  await page.getByRole('radio',{name:'Standard double · VALUE',exact:true}).check();await page.getByRole('button',{name:'Recheck selected rooms'}).click();await expect(page.getByRole('heading',{name:'Current room rates checked'})).toBeVisible();
  // UI expiry boundary using an expired observation; actual elapsed-clock expiry is covered by HotelIntegrationTest.
  await page.route('**/api/v1/hotels/offers/*/reprice',async route=>{const response=await route.fetch();const result=await response.json();await route.fulfill({response,json:{...result,validUntil:'2000-01-01T00:00:00Z'}});});
  await page.getByRole('button',{name:'Recheck selected rooms'}).click();await expect(page.getByText('This rate check expired. Recheck the rooms before relying on these prices.',{exact:true})).toBeVisible();await expect(page.getByRole('heading',{name:'Current room rates checked'})).not.toBeVisible();
});
test('AC-041 — provider outage, empty search and validation remain distinct',async({page})=>{
  await page.goto('/hotels');await hotelDates(page);await page.getByLabel('Destination',{exact:true}).fill('Goa');await page.getByRole('button',{name:'Search hotels',exact:true}).click();await expect(page.getByText('Some hotel providers are unavailable.',{exact:true})).toBeVisible();
  await page.getByLabel('Destination',{exact:true}).fill('Jaipur');await page.getByRole('button',{name:'Search hotels',exact:true}).click();await expect(page.getByRole('heading',{name:'No matching stays'})).toBeVisible();
  await page.getByLabel('Destination',{exact:true}).fill('Shimla');await page.getByRole('button',{name:'Search hotels',exact:true}).click();await expect(page.getByRole('main').getByRole('alert')).toContainText('Hotel providers are unavailable');
  const body={destination:'Delhi',checkIn:travelDate(300),checkOut:travelDate(302),rooms:[{adults:2,childAges:[18]}]};const invalid=await page.request.post('/api/v1/hotels/search',{data:body});expect(invalid.status()).toBe(400);schema('Problem',await invalid.json());
});
test('AC-038/040 — room APIs obey schemas, no guest data and missing context fails safely',async({page})=>{
  const search=await page.request.post('/api/v1/hotels/search',{data:{destination:'Delhi',checkIn:travelDate(300),checkOut:travelDate(302),rooms:[{adults:2,childAges:[]}]}});expect(search.status()).toBe(200);const s=await search.json();schema('HotelSearchPage',s);
  const property=s.items[0].property.id;const rooms=await page.request.get(`/api/v1/hotels/${property}/rooms?searchId=${s.id}`);expect(rooms.status()).toBe(200);const r=await rooms.json();schema('HotelRooms',r);
  const detail=await page.request.get(`/api/v1/hotels/${property}?searchId=${s.id}`);schema('HotelRooms',await detail.json());
  expect((await page.request.get(`/api/v1/hotels/${property}/rooms`)).status()).toBe(400);
  const o=r.offers.find((o:{planCode:string})=>o.planCode==='FLEX');const reprice=await page.request.post(`/api/v1/hotels/offers/${o.id}/reprice`,{data:{offerIds:[o.id]}});expect(reprice.status()).toBe(200);schema('HotelReprice',await reprice.json());
  await page.goto(`/hotels/${property}`);await expect(page.getByRole('heading',{name:'Choose dates and rooms first'})).toBeVisible();
});
