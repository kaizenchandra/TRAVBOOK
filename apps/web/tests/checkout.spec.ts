import {test,expect,type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import contract from '../../../contracts/openapi.json' with {type:'json'};
import {travelDate} from '../src/lib/format';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
test.use({trace:'off'});
const ajv=new Ajv2020({strict:false,allErrors:true});addFormats(ajv);
function schema(name:string,data:unknown){const validate=ajv.compile({$ref:'#/components/schemas/'+name,components:contract.components});expect(validate(data),JSON.stringify(validate.errors)).toBe(true);}
async function login(page:Page,user='alice'){
  await page.goto('/account');await page.getByRole('link',{name:'Continue to secure sign-in'}).click();await page.getByLabel('Username or email',{exact:true}).fill(user);await page.getByLabel('Password',{exact:true}).fill(user+'-local-only');await page.getByRole('button',{name:'Sign In',exact:true}).click();await expect(page.getByRole('heading',{name:'You’re signed in'})).toBeVisible();
}
async function headers(page:Page){const s=await(await page.request.get('/auth/session')).json();return {'Idempotency-Key':crypto.randomUUID(),[s.csrfHeaderName]:s.csrfToken};}
async function intent(page:Page,nonRefundable=false){
  const search=await page.request.post('/api/v1/flights/search',{data:{origin:'DEL',destination:'BOM',departureDate:travelDate(2),returnDate:null,adults:1,children:0,infants:0,cabin:'ECONOMY'}});expect(search.status()).toBe(200);
  const offers=(await search.json()).offers;const offer=nonRefundable?offers.find((o:{cancellationPolicy:string})=>o.cancellationPolicy.startsWith('MOCK-NONE-V1')):offers[0];expect(offer).toBeDefined();
  const quote=await page.request.post('/api/v1/quotes',{headers:await headers(page),data:{offerId:offer.id}});expect(quote.status()).toBe(201);const q=await quote.json();
  expect((await page.request.post('/api/v1/quotes/'+q.id+'/acceptance',{headers:await headers(page),data:{version:1}})).status()).toBe(200);
  const booking=await page.request.post('/api/v1/bookings',{headers:await headers(page),data:{quoteId:q.id,quoteVersion:1,travellers:[{givenName:'Alex',familyName:'Example',dateOfBirth:'1990-01-01'}]}});expect(booking.status()).toBe(202);const id=(await booking.json()).id;
  await page.goto('/bookings/'+id);await expect(page.getByText('Validation complete — awaiting payment',{exact:true})).toBeVisible({timeout:20_000});return id;
}
async function start(page:Page,scenario='SUCCESS'){
  await page.getByLabel('Demonstration outcome').selectOption(scenario);await page.getByRole('checkbox',{name:/I consent to a MOCK/}).check();await page.getByRole('button',{name:'Start MOCK checkout',exact:true}).click();
}
test('AC-023/024/027 — explicit keyboard MOCK checkout, independent states and My Trips',async({page},info)=>{
  await login(page);const id=await intent(page);
  const denied=await page.request.post('/api/v2/bookings/'+id+'/checkout',{headers:{'Idempotency-Key':crypto.randomUUID()},data:{scenario:'SUCCESS',consent:true}});expect(denied.status()).toBe(403);
  await expect(page.getByRole('button',{name:'Start MOCK checkout',exact:true})).toBeDisabled();
  await page.getByRole('checkbox',{name:/I consent to a MOCK/}).focus();await page.keyboard.press('Space');await page.keyboard.press('Tab');await page.keyboard.press('Enter');
  await expect(page.getByText('MOCK booking confirmed',{exact:true})).toBeVisible({timeout:20_000});
  const state=await(await page.request.get('/api/v2/bookings/'+id)).json();schema('BookingDetailsV2',state);expect(state.checkout.paymentStatus).toBe('CAPTURED');expect(state.checkout.reservationStatus).toBe('CONFIRMED');expect(state.checkout.mode).toBe('MOCK');
  expect((await page.request.get('/api/v1/bookings/'+id)).status()).toBe(409);
  await page.reload();await expect(page.getByText('MOCK booking confirmed',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Start MOCK checkout',exact:true})).not.toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
  await page.screenshot({path:info.outputPath('mock-checkout.png'),fullPage:true});
  await page.goto('/trips');const card=page.locator('article').filter({has:page.locator('a[href="/bookings/'+id+'"]')});await expect(card.getByText('MOCK booking confirmed',{exact:true})).toBeVisible();
  schema('BookingListV3',await(await page.request.get('/api/v3/me/bookings')).json());
});
test('AC-025 — supplier and capture rejection show truthful compensated failure',async({page})=>{
  await login(page);
  for(const scenario of ['RESERVATION_REJECTED','CAPTURE_DECLINED']){
    const id=await intent(page);await start(page,scenario);await expect(page.getByText('MOCK checkout failed',{exact:true})).toBeVisible({timeout:20_000});
    const state=await(await page.request.get('/api/v2/bookings/'+id)).json();schema('BookingDetailsV2',state);expect(state.checkout.paymentStatus).toBe('VOIDED');expect(state.checkout.reservationStatus).toBe(scenario==='RESERVATION_REJECTED'?'REJECTED':'CANCELLED');
    await expect(page.getByText('MOCK booking confirmed',{exact:true})).not.toBeVisible();
  }
});
test('AC-026/027 — lost acknowledgement and supplier response recover through refresh',async({page})=>{
  await login(page);const id=await intent(page);let submitted=0;
  await page.route('**/api/v2/bookings/'+id+'/checkout',async route=>{submitted++;const response=await route.fetch();expect(response.status()).toBe(202);schema('CheckoutAcknowledgement',await response.json());await route.abort('failed');});
  await start(page,'RESERVATION_RESPONSE_LOST');await expect(page.getByRole('button',{name:'Recover saved checkout'})).toBeVisible();
  await page.unroute('**/api/v2/bookings/'+id+'/checkout');await page.reload();await expect(page.getByText('MOCK booking confirmed',{exact:true})).toBeVisible({timeout:20_000});expect(submitted).toBe(1);
  const state=await(await page.request.get('/api/v2/bookings/'+id)).json();expect(state.checkout.paymentStatus).toBe('CAPTURED');expect(state.checkout.reservationStatus).toBe('CONFIRMED');
});
test('AC-029/030 — owned MOCK document preparation, download, refresh and denied owner switch',async({page,browser},info)=>{
  await login(page);const id=await intent(page);await start(page);await expect(page.getByText('MOCK booking confirmed',{exact:true})).toBeVisible({timeout:20_000});
  const section=page.getByRole('region',{name:'MOCK documents'});await expect(section.getByText('No summary has been generated yet.')).toBeVisible();
  expect((await page.request.post('/api/v1/bookings/'+id+'/documents',{headers:{'Idempotency-Key':crypto.randomUUID()},data:{kind:'MOCK_CONFIRMATION_SUMMARY'}})).status()).toBe(403);
  const response=page.waitForResponse(r=>r.url().endsWith('/bookings/'+id+'/documents')&&r.request().method()==='POST');
  await section.getByRole('button',{name:'Prepare MOCK summary'}).click();const created=await response;expect(created.status()).toBe(201);const metadata=await created.json();schema('TripDocument',metadata);
  const pending=page.waitForEvent('download');await section.getByRole('button',{name:'Download MOCK summary'}).click();const download=await pending;
  expect(download.suggestedFilename()).toBe('travbook-MOCK-summary-'+metadata.id+'.txt');const saved=info.outputPath('MOCK-summary.txt');await download.saveAs(saved);const bytes=await readFile(saved);
  expect(bytes.length).toBe(metadata.byteLength);expect(createHash('sha256').update(bytes).digest('hex')).toBe(metadata.sha256);
  expect(bytes.toString('utf8')).toContain('NOT A TICKET OR TAX INVOICE');expect(bytes.toString('utf8')).toContain('Alex Example');expect(bytes.toString('utf8')).not.toContain('1990-01-01');
  expect(bytes.toString('utf8')).toContain('Departure (local):');expect(bytes.toString('utf8')).toContain('+05:30');
  await page.reload();await expect(section.getByRole('button',{name:'Download MOCK summary'})).toBeVisible();await expect(section.getByRole('button',{name:'Prepare MOCK summary'})).not.toBeVisible();
  schema('TripDocumentList',await(await page.request.get('/api/v1/bookings/'+id+'/documents')).json());
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
  const other=await browser.newContext({baseURL:'http://127.0.0.1:3000'});
  try{const bob=await other.newPage();await login(bob,'bob');expect((await bob.request.get(metadata.downloadUrl)).status()).toBe(404);
    const cookie=(await other.cookies()).find(c=>c.name==='TRAVBOOK_SESSION')!;await page.context().addCookies([cookie]);await section.getByRole('button',{name:'Download MOCK summary'}).click();
    await expect(page.getByRole('main').getByRole('alert')).toContainText('Booking intent not found');await expect(page.getByText('Alex Example',{exact:true})).not.toBeVisible();
  }finally{await other.close();}
});

test('AC-031/033/035 — quote, explicit cancellation, delayed refund and document blocking',async({page},info)=>{
  const renderErrors:string[]=[];page.on('pageerror',error=>renderErrors.push(error.message));page.on('console',message=>{if(message.type()==='error'&&/same key|hydration|Cannot update a component|Rendered (more|fewer) hooks/i.test(message.text()))renderErrors.push(message.text());});
  await login(page);const id=await intent(page);await start(page);await expect(page.getByText('MOCK booking confirmed',{exact:true})).toBeVisible({timeout:20_000});
  const doc=await page.request.post('/api/v1/bookings/'+id+'/documents',{headers:await headers(page),data:{kind:'MOCK_CONFIRMATION_SUMMARY'}});expect(doc.status()).toBe(201);const metadata=await doc.json();
  const section=page.getByRole('region',{name:'Cancellation and refund'});
  const response=page.waitForResponse(r=>r.url().endsWith('/cancellation-quotes')&&r.request().method()==='POST');await section.getByRole('button',{name:'Request cancellation quote'}).click();
  const q=await(await response).json();schema('CancellationQuote',q);expect(q.refundAmount.amount).toBe(q.total.amount);expect(q.retainedAmount.amount).toBe('0.00');
  expect((await page.request.post('/api/v1/bookings/'+id+'/cancellations',{headers:{'Idempotency-Key':crypto.randomUUID()},data:{quoteId:q.id,quoteVersion:1,confirmed:true,scenario:'SUCCESS'}})).status()).toBe(403);
  await expect(section.getByRole('button',{name:'Confirm MOCK cancellation'})).toBeDisabled();await page.getByLabel('Cancellation demonstration').selectOption('REFUND_DELAYED');
  await section.getByRole('checkbox').focus();await page.keyboard.press('Space');await page.keyboard.press('Tab');await page.keyboard.press('Enter');
  await expect(section.getByText('MOCK booking cancelled',{exact:true})).toBeVisible({timeout:20_000});await expect(section.getByText('Refund pending — cancellation does not mean the refund has completed.')).toBeVisible();
  expect((await page.request.get(metadata.downloadUrl)).status()).toBe(409);await expect(page.getByRole('button',{name:'Download MOCK summary'})).not.toBeVisible();
  await expect(section.getByText('MOCK refund completed.',{exact:true})).toBeVisible({timeout:20_000});const refunds=await(await page.request.get('/api/v1/bookings/'+id+'/refunds')).json();schema('RefundList',refunds);expect(refunds.items[0].refundStatus).toBe('SUCCEEDED');
  await page.reload();await expect(section.getByText('MOCK refund completed.',{exact:true})).toBeVisible();await expect(section.getByRole('button',{name:'Confirm MOCK cancellation'})).not.toBeVisible();
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.screenshot({path:info.outputPath('MOCK-refund.png'),fullPage:true});await page.goto('/trips');await expect(page.locator('article').filter({has:page.locator('a[href="/bookings/'+id+'"]')}).getByText('MOCK booking cancelled',{exact:true})).toBeVisible();expect(renderErrors).toEqual([]);
});

test('AC-032/034/035 — lost cancellation acknowledgement and refund response recover once',async({page,browser})=>{
  await login(page);const id=await intent(page);await start(page);await expect(page.getByText('MOCK booking confirmed',{exact:true})).toBeVisible({timeout:20_000});
  const section=page.getByRole('region',{name:'Cancellation and refund'});await section.getByRole('button',{name:'Request cancellation quote'}).click();await expect(page.getByLabel('Cancellation demonstration')).toBeVisible();
  let submitted=0,ack:{id:string}|undefined;
  await page.route('**/api/v1/bookings/'+id+'/cancellations',async route=>{submitted++;const response=await route.fetch();expect(response.status()).toBe(202);const body=await response.json();schema('CancellationAcknowledgement',body);ack=body;await route.abort('failed');});
  await page.getByLabel('Cancellation demonstration').selectOption('REFUND_RESPONSE_LOST');await section.getByRole('checkbox').check();await section.getByRole('button',{name:'Confirm MOCK cancellation'}).click();await expect(section.getByRole('button',{name:'Recover cancellation status'})).toBeVisible();
  await page.unroute('**/api/v1/bookings/'+id+'/cancellations');await page.reload();await expect(section.getByText('MOCK refund completed.',{exact:true})).toBeVisible({timeout:20_000});expect(submitted).toBe(1);
  const state=await(await page.request.get('/api/v3/bookings/'+id)).json();schema('BookingDetailsV3',state);expect(state.cancellation.id).toBe(ack!.id);
  const other=await browser.newContext({baseURL:'http://127.0.0.1:3000'});try{const bob=await other.newPage();await login(bob,'bob');expect((await bob.request.get('/api/v1/bookings/'+id+'/refunds')).status()).toBe(404);expect((await bob.request.post('/api/v1/bookings/'+id+'/cancellation-quotes',{headers:await headers(bob),data:{scope:'FULL_BOOKING'}})).status()).toBe(404);}finally{await other.close();}
});

test('AC-031/033 — explicit zero-refund policy cancels without claiming a refund',async({page})=>{
  await login(page);const id=await intent(page,true);await start(page);await expect(page.getByText('MOCK booking confirmed',{exact:true})).toBeVisible({timeout:20_000});
  const section=page.getByRole('region',{name:'Cancellation and refund'});await section.getByRole('button',{name:'Request cancellation quote'}).click();await expect(section.getByText(/MOCK-NONE-V1/)).toBeVisible();
  await section.getByRole('checkbox').check();await section.getByRole('button',{name:'Confirm MOCK cancellation'}).click();await expect(section.getByText('No refund is due under the accepted zero-refund policy.')).toBeVisible({timeout:20_000});
  const refunds=await(await page.request.get('/api/v1/bookings/'+id+'/refunds')).json();schema('RefundList',refunds);expect(refunds.items).toEqual([]);await expect(section.getByText('MOCK refund completed.',{exact:true})).not.toBeVisible();
});
