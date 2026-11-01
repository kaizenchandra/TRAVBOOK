import {test,expect,type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import contract from '../../../contracts/openapi.json' with {type:'json'};
import {travelDate} from '../src/lib/format';
test.use({trace:'off'}); // Traveller bodies, auth codes and cookies must not be captured in traces.
const ajv=new Ajv2020({strict:false,allErrors:true});addFormats(ajv);
function schema(name:string,data:unknown){const validate=ajv.compile({$ref:'#/components/schemas/'+name,components:contract.components});expect(validate(data),JSON.stringify(validate.errors)).toBe(true);}
async function login(page:Page,user='alice') {
  await page.goto('/account');await page.getByRole('link',{name:'Continue to secure sign-in'}).click();
  await page.getByLabel('Username or email',{exact:true}).fill(user);await page.getByLabel('Password',{exact:true}).fill(user+'-local-only');
  await page.getByRole('button',{name:'Sign In',exact:true}).click();await expect(page.getByRole('heading',{name:'You’re signed in'})).toBeVisible();
}
async function headers(page:Page,key=crypto.randomUUID()){const session=await(await page.request.get('/auth/session')).json();return {'Idempotency-Key':key,[session.csrfHeaderName]:session.csrfToken};}
async function acceptedQuote(page:Page) {
  const search=await page.request.post('/api/v1/flights/search',{data:{origin:'DEL',destination:'BOM',departureDate:travelDate(2),returnDate:null,adults:1,children:0,infants:0,cabin:'ECONOMY'}});expect(search.status()).toBe(200);
  const q=await page.request.post('/api/v1/quotes',{headers:await headers(page),data:{offerId:(await search.json()).offers[0].id}});expect(q.status()).toBe(201);const quote=await q.json();
  const accept=await page.request.post('/api/v1/quotes/'+quote.id+'/acceptance',{headers:await headers(page),data:{version:quote.version}});expect(accept.status()).toBe(200);return quote;
}
const travellers=[{givenName:'Alex',familyName:'Example',dateOfBirth:'1990-01-01'}];
test('AC-013/014 — accepted quote → travellers → durable status → My Trips',async({page},info)=>{
  await login(page);const q=await acceptedQuote(page);await page.goto('/quotes/'+q.id);
  await page.getByRole('link',{name:'Add travellers and create intent'}).click();await expect(page.getByRole('heading',{name:'Traveller details'})).toBeVisible();
  await page.getByLabel('Given name',{exact:true}).fill('Alex');await page.getByLabel('Family name',{exact:true}).fill('Example');await page.getByLabel('Date of birth',{exact:true}).fill('1990-01-01');
  await page.getByRole('checkbox',{name:/I reviewed these travellers/}).check();
  await page.getByRole('button',{name:'Create booking intent',exact:true}).click();await expect(page).toHaveURL(/\/bookings\/[0-9a-f-]{36}$/);
  const id=new URL(page.url()).pathname.split('/').pop();await expect(page.getByText('Validation complete — awaiting payment',{exact:true})).toBeVisible({timeout:20_000});
  await expect(page.getByText('NOT REQUESTED',{exact:true})).toBeVisible();await expect(page.getByText('NOT STARTED',{exact:true})).toBeVisible();
  const state=await(await page.request.get('/api/v1/bookings/'+id)).json();schema('BookingDetails',state);expect(state.workflow.terminal).toBe(true);
  const history=page.getByRole('region',{name:'Intent history'});
  await expect(history.getByText('Offer validation completed',{exact:true})).toBeVisible({timeout:20_000});
  const events=await(await page.request.get('/api/v1/bookings/'+id+'/timeline')).json();schema('BookingTimeline',events);
  expect(events.items.map((e:{version:number})=>e.version)).toEqual([1,2,3]);expect(events.deliveryState).toBe('CAUGHT_UP');
  expect(JSON.stringify(events)).not.toContain('1990-01-01');
  await page.reload();await expect(page.getByText('Alex Example',{exact:true})).toBeVisible();
  expect(await page.evaluate(()=>Object.keys(localStorage).concat(Object.keys(sessionStorage)))).toEqual([]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
  await page.screenshot({path:info.outputPath('booking-intent.png'),fullPage:true});
  await page.goto('/trips');await expect(page.locator('a[href="/bookings/'+id+'"]')).toBeVisible();
  const list=await(await page.request.get('/api/v1/me/bookings')).json();schema('BookingList',list);expect(JSON.stringify(list)).not.toContain('1990-01-01');
});
test('AC-015 — refresh after a lost acknowledgement recovers one intent without replaying traveller data',async({page})=>{
  await login(page);const q=await acceptedQuote(page),key=crypto.randomUUID();
  const response=await page.request.post('/api/v1/bookings',{headers:await headers(page,key),data:{quoteId:q.id,quoteVersion:q.version,travellers}});expect(response.status()).toBe(202);const ack=await response.json();schema('BookingAcknowledgement',ack);
  await page.goto('/bookings/new?quoteId='+q.id+'&requestId='+key);await page.reload();
  await expect(page.getByRole('heading',{name:'Your booking intent is already saved'})).toBeVisible();await expect(page.getByRole('button',{name:'Create booking intent',exact:true})).not.toBeVisible();
  await page.getByRole('link',{name:'View saved booking intent'}).click();await expect(page).toHaveURL(new RegExp('/bookings/'+ack.id+'$'));
  const replay=await page.request.post('/api/v1/bookings',{headers:await headers(page,key),data:{quoteId:q.id,quoteVersion:q.version,travellers}});expect(replay.status()).toBe(202);expect(await replay.json()).toEqual(ack);
  const requirements=await(await page.request.get('/api/v1/quotes/'+q.id+'/booking-requirements')).json();schema('BookingRequirements',requirements);expect(requirements.existingBookingId).toBe(ack.id);
});
test('AC-016 — ownership, CSRF and denied refresh never expose cached private traveller data',async({page,browser})=>{
  await login(page);const q=await acceptedQuote(page);
  const missing=await page.request.post('/api/v1/bookings',{headers:{'Idempotency-Key':crypto.randomUUID()},data:{quoteId:q.id,quoteVersion:q.version,travellers}});expect(missing.status()).toBe(403);
  const created=await page.request.post('/api/v1/bookings',{headers:await headers(page),data:{quoteId:q.id,quoteVersion:q.version,travellers}});expect(created.status()).toBe(202);const ack=await created.json();
  await page.goto('/bookings/'+ack.id);await expect(page.getByText('Alex Example',{exact:true})).toBeVisible();
  const other=await browser.newContext({baseURL:'http://127.0.0.1:3000'});try{
    const bob=await other.newPage();await login(bob,'bob');expect((await bob.request.get('/api/v1/bookings/'+ack.id)).status()).toBe(404);
    expect((await bob.request.get('/api/v1/bookings/'+ack.id+'/timeline')).status()).toBe(404);
    const refused=await bob.request.post('/api/v1/bookings',{headers:await headers(bob),data:{quoteId:q.id,quoteVersion:q.version,travellers}});expect(refused.status()).toBe(404);
    // Simulate a user switch in another tab, retaining this tab's query cache.
    const cookie=(await other.cookies()).find(c=>c.name==='TRAVBOOK_SESSION')!;await page.context().addCookies([cookie]);
    await page.getByRole('button',{name:'Refresh status',exact:true}).click();await expect(page.getByRole('main').getByRole('alert')).toContainText('Booking intent not found');
    await expect(page.getByText('Alex Example',{exact:true})).not.toBeVisible();
  }finally{await other.close();}
});
test('AC-022 — delayed history, read failure and lost ownership remain honest and recoverable',async({page,browser})=>{
  await login(page);const q=await acceptedQuote(page);
  const response=await page.request.post('/api/v1/bookings',{headers:await headers(page),data:{quoteId:q.id,quoteVersion:q.version,travellers}});expect(response.status()).toBe(202);
  const ack=await response.json();await page.goto('/bookings/'+ack.id);
  const history=page.getByRole('region',{name:'Intent history'});
  await expect(history.getByText('Offer validation completed',{exact:true})).toBeVisible({timeout:20_000});
  const endpoint='**/api/v1/bookings/'+ack.id+'/timeline*';
  const realHistory=await(await page.request.get('/api/v1/bookings/'+ack.id+'/timeline')).json();
  let deliveryState='PENDING';
  await page.route(endpoint,route=>route.fulfill({status:200,contentType:'application/json',body:JSON.stringify({...realHistory,items:[],deliveredVersion:0,deliveryState})}));
  await history.getByRole('button',{name:'Refresh history'}).click();await expect(history.getByRole('status')).toContainText('History is catching up');
  await expect(history.getByText('Offer validation completed',{exact:true})).not.toBeVisible();
  deliveryState='ATTENTION_REQUIRED';await history.getByRole('button',{name:'Refresh history'}).click();
  await expect(history.getByRole('status')).toContainText('History delivery needs operational review');await page.unroute(endpoint);
  await page.route(endpoint,route=>route.fulfill({status:503,contentType:'application/problem+json',body:JSON.stringify({type:'urn:travbook:problem:dependency_unavailable',title:'DEPENDENCY_UNAVAILABLE',status:503,code:'DEPENDENCY_UNAVAILABLE',detail:'History temporarily unavailable.',correlationId:crypto.randomUUID()})}));
  await history.getByRole('button',{name:'Refresh history'}).click();await expect(history.getByRole('alert')).toContainText('History temporarily unavailable.');
  await expect(history.getByText('Intent saved',{exact:true})).not.toBeVisible();
  await expect(page.getByText('Validation complete — awaiting payment',{exact:true})).toBeVisible();
  await page.unroute(endpoint);await history.getByRole('button',{name:'Try again'}).click();
  await expect(history.getByText('Offer validation completed',{exact:true})).toBeVisible();
  const other=await browser.newContext({baseURL:'http://127.0.0.1:3000'});
  try{
    const bob=await other.newPage();await login(bob,'bob');const cookie=(await other.cookies()).find(c=>c.name==='TRAVBOOK_SESSION')!;
    await page.context().addCookies([cookie]);await history.getByRole('button',{name:'Refresh history'}).click();
    await expect(page.getByRole('main').getByRole('alert')).toContainText('Booking intent not found');
    await expect(page.getByText('Alex Example',{exact:true})).not.toBeVisible();await expect(history).not.toBeVisible();
  }finally{await other.close();}
});
