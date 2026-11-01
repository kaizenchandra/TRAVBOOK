import {test,expect,type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import contract from '../../../contracts/openapi.json' with {type:'json'};
import {travelDate} from '../src/lib/format';
test.use({trace:'off'});test.setTimeout(90_000);
const ajv=new Ajv2020({strict:false,allErrors:true});addFormats(ajv);
const cabs=new WeakMap<Page,string[]>();
function schema(name:string,value:unknown){const validate=ajv.compile({$ref:'#/components/schemas/'+name,components:contract.components});expect(validate(value),JSON.stringify(validate.errors)).toBe(true);}
async function login(page:Page,user='alice'){await page.goto('/account');await page.getByRole('link',{name:'Continue to secure sign-in'}).click();await page.getByLabel('Username or email',{exact:true}).fill(user);await page.getByLabel('Password',{exact:true}).fill(user+'-local-only');await page.getByRole('button',{name:'Sign In',exact:true}).click();await expect(page.getByRole('heading',{name:'You’re signed in'})).toBeVisible();}
async function headers(page:Page,key=crypto.randomUUID()){const s=await(await page.request.get('/auth/session')).json();return {'Idempotency-Key':key,[s.csrfHeaderName]:s.csrfToken};}
async function post(page:Page,path:string,data:unknown,status:number){const r=await page.request.post(path,{headers:await headers(page),data});expect(r.status()).toBe(status);return r.json();}
async function open(page:Page){await page.getByRole('link',{name:'API sandbox',exact:true}).click();await expect(page.getByRole('heading',{name:'Dummy messaging and privacy jobs'})).toBeVisible();}
function panel(page:Page,title:string){return page.locator('section').filter({has:page.getByRole('heading',{name:title,exact:true})});}
async function queue(page:Page,kind:string,scenario='NORMAL'){
  await page.getByLabel('Simulation type',{exact:true}).selectOption(kind);await page.getByLabel('Provider behavior',{exact:true}).selectOption(scenario);await page.getByRole('checkbox',{name:'I understand this queues only a simulation.'}).check();
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/demo/operations')&&r.request().method()==='POST');await page.getByRole('button',{name:'Queue simulation',exact:true}).click();const r=await response;expect(r.status()).toBe(202);const op=await r.json();schema('DemoOperation',op);return op;
}
async function currentCab(page:Page,id:string){const r=await page.request.get('/api/v2/journey-bookings/'+id);expect(r.status()).toBe(200);return r.json();}
async function cancelCab(page:Page,id:string){const b=await currentCab(page,id);if(b.status!=='CONFIRMED')return;const q=await post(page,`/api/v2/journey-bookings/${id}/cancellation-quotes`,{componentIds:b.activeComponentIds},201);await post(page,`/api/v2/journey-bookings/${id}/cancellations`,{quoteId:q.id,consent:true,simulateDelay:false,responseLoss:false},202);await expect.poll(async()=>(await currentCab(page,id)).refund,{timeout:25_000,intervals:[500,1000,2000]}).toBe('SUCCEEDED');}
test.beforeEach(async({page})=>{cabs.set(page,[]);await login(page);await open(page);});
test.afterEach(async({page})=>{for(const id of cabs.get(page)??[])await cancelCab(page,id);});

test('AC-085 — dummy message lost acknowledgement, durable retry and bounded permanent failure',async({page},info)=>{
  const area=panel(page,'Dummy messaging and privacy jobs');let accepted='',attempts=0;const keys:string[]=[];
  await page.getByLabel('Provider behavior',{exact:true}).selectOption('RETRY_ONCE');await page.getByRole('checkbox',{name:'I understand this queues only a simulation.'}).focus();await page.keyboard.press('Space');
  await page.route('**/api/v1/demo/operations',async route=>{if(route.request().method()!=='POST'){await route.continue();return;}attempts++;keys.push(route.request().headers()['idempotency-key']);const r=await route.fetch();expect(r.status()).toBe(202);const result=await r.json();if(accepted)expect(result.id).toBe(accepted);accepted=result.id;if(attempts===1)await route.abort('failed');else await route.fulfill({response:r});});
  await page.getByRole('button',{name:'Queue simulation',exact:true}).press('Enter');await expect(area.getByRole('alert')).toBeVisible();await page.getByRole('button',{name:'Queue simulation',exact:true}).click();
  await expect(area.getByText('SIMULATED_DELIVERED',{exact:true})).toBeVisible({timeout:20_000});expect(attempts).toBe(2);expect(new Set(keys).size).toBe(1);await expect(area).toContainText('Attempts 2/3');
  await page.unroute('**/api/v1/demo/operations');await page.reload();await expect(area.getByText('SIMULATED_DELIVERED',{exact:true})).toBeVisible();
  const failed=await queue(page,'SMS','FAIL');await expect(area.getByText('FAILED',{exact:true})).toBeVisible({timeout:25_000});await expect(area).toContainText('Attempts 3/3');
  const result=await(await page.request.get('/api/v1/demo/operations/'+failed.id)).json();schema('DemoOperation',result);expect(result.reference).toBeNull();expect(result.terminal).toBe(true);
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.getByRole('heading',{name:'API sandbox',exact:true}).click();await page.screenshot({path:info.outputPath('dummy-messaging.png'),fullPage:true});
});

test('AC-088 — private export preview and simulated erasure preserve the real profile',async({page,browser})=>{
  const profile=await(await page.request.get('/api/v1/me/profile')).json();await page.getByRole('button',{name:'Preview my profile data'}).click();await expect(page.getByLabel('Profile export preview')).toContainText(profile.customerId);
  const preview=await(await page.request.get('/api/v1/demo/privacy-preview')).json();schema('DemoPrivacyPreview',preview);expect(preview.completeExport).toBe(false);expect(preview.profile).toEqual(profile);
  const op=await queue(page,'PRIVACY_ERASURE');await expect(panel(page,'Dummy messaging and privacy jobs').getByText('SIMULATED_REVIEW_REQUIRED',{exact:true})).toBeVisible({timeout:15_000});expect(await(await page.request.get('/api/v1/me/profile')).json()).toEqual(profile);
  const input={expectedCustomerId:profile.customerId,kind:'EMAIL',scenario:'NORMAL',consent:true};expect((await page.request.post('/api/v1/demo/operations',{headers:{'Idempotency-Key':crypto.randomUUID()},data:input})).status()).toBe(403);
  const alice=(await page.context().cookies()).find(c=>c.name==='TRAVBOOK_SESSION')!;const context=await browser.newContext({baseURL:'http://127.0.0.1:3000'});
  try{const bob=await context.newPage();await login(bob,'bob');expect((await bob.request.get('/api/v1/demo/operations/'+op.id)).status()).toBe(404);expect((await bob.request.post('/api/v1/demo/operations',{headers:await headers(bob),data:input})).status()).toBe(409);
    await page.context().addCookies([(await context.cookies()).find(c=>c.name==='TRAVBOOK_SESSION')!]);await page.getByRole('button',{name:'Load my ledger report'}).click();await expect(page.getByRole('main').getByRole('alert')).toContainText('Your signed-in account changed');await expect(page.getByLabel('Profile export preview')).not.toBeVisible();
  }finally{await page.context().addCookies([alice]);await context.close();}
});

test('AC-086 — coupon eligibility and rejection use an owned quote without altering its price',async({page,browser})=>{
  const search=await post(page,'/api/v1/flights/search',{origin:'DEL',destination:'BOM',departureDate:travelDate(65),returnDate:null,adults:1,children:0,infants:0,cabin:'ECONOMY'},200);
  const q=await post(page,'/api/v1/quotes',{offerId:search.offers[0].id},201);const before=await(await page.request.get('/api/v1/quotes/'+q.id)).json();
  await page.getByLabel('Flight quote ID',{exact:true}).fill(q.id);const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/demo/coupons/preview'));await page.getByRole('button',{name:'Preview coupon',exact:true}).click();const p=await(await response).json();schema('DemoCouponPreview',p);expect(p.eligible).toBe(true);expect(p.applied).toBe(false);expect(p.originalTotal).toEqual(q.total);
  await expect(panel(page,'Coupon preview').getByRole('status')).toContainText('ELIGIBLE');await expect(panel(page,'Coupon preview')).toContainText('No coupon is applied or redeemed');
  await page.getByLabel('Demo coupon code',{exact:true}).fill('DEMOEXPIRED');await page.getByRole('button',{name:'Preview coupon',exact:true}).click();await expect(panel(page,'Coupon preview').getByRole('status')).toContainText('EXPIRED CODE');
  await page.getByLabel('Demo coupon code',{exact:true}).fill('UNKNOWN');await page.getByRole('button',{name:'Preview coupon',exact:true}).click();await expect(panel(page,'Coupon preview').getByRole('status')).toContainText('UNKNOWN CODE');expect(await(await page.request.get('/api/v1/quotes/'+q.id)).json()).toEqual(before);
  const other=await browser.newContext({baseURL:'http://127.0.0.1:3000'});try{const bob=await other.newPage();await login(bob,'bob');expect((await bob.request.post('/api/v1/demo/coupons/preview',{headers:await headers(bob),data:{quoteId:q.id,code:'DEMO10'}})).status()).toBe(404);}finally{await other.close();}
});

test('AC-087/089 — persisted MOCK cab workflow feeds synthetic GPS and owned ledger/payment APIs',async({page},info)=>{
  const before=await(await page.request.get('/api/v1/demo/financial-report')).json();schema('DemoFinancialReport',before);
  const search=await post(page,'/api/v1/cabs/search',{type:'AIRPORT',pickup:'DELHI_AIRPORT',dropoff:'DELHI_CENTRE',date:travelDate(info.project.name==='mobile'?82:81),time:'10:00',returnDate:null,returnTime:null,passengers:1,luggage:1,hours:0},200);
  const q=await post(page,'/api/v2/journey-quotes',{selection:{kind:'CAB',sourceId:search.id,offerId:search.items[0].id,seats:[],boardingId:null,droppingId:null},fundingPlan:'FULL'},201);
  await post(page,`/api/v2/journey-quotes/${q.id}/acceptance`,{version:q.version,consent:true},200);
  const b=await post(page,'/api/v2/journey-bookings',{quoteId:q.id,passengers:[{name:'Demo Traveller',age:30}],email:'demo@example.test',scenario:'NORMAL'},202);cabs.get(page)!.push(b.id);
  await post(page,`/api/v2/journey-bookings/${b.id}/payment`,{},202);await expect.poll(async()=>(await currentCab(page,b.id)).status,{timeout:20_000,intervals:[500,1000,2000]}).toBe('CONFIRMED');
  await page.getByLabel('Owned cab booking ID',{exact:true}).fill(b.id);await page.getByRole('button',{name:'Load synthetic position',exact:true}).click();const gps=panel(page,'Synthetic cab GPS');await expect(gps).toContainText('28.550000, 77.090000');
  const first=await(await page.request.get('/api/v1/demo/gps/'+b.id)).json();schema('DemoGps',first);expect(first.stale).toBe(false);
  await page.getByRole('checkbox',{name:'Advance the synthetic trip only.'}).check();await page.getByRole('button',{name:'Advance simulation stage',exact:true}).click();await expect(gps).toContainText('28.552000, 77.095000');
  await page.getByLabel('Payment account ID',{exact:true}).fill(b.id);await page.getByRole('button',{name:'Read payment status',exact:true}).click();await expect(panel(page,'Dummy payment status').getByRole('status')).toContainText('CAPTURED · MOCK');
  const payment=await(await page.request.get('/api/v1/demo/payments/'+b.id)).json();schema('DemoPayment',payment);expect(payment.amount).toEqual(q.selection.total);
  await page.getByRole('button',{name:'Load my ledger report'}).click();await expect(panel(page,'My MOCK ledger report').getByRole('status')).toContainText('NOT_RECONCILED');const report=await(await page.request.get('/api/v1/demo/financial-report')).json();schema('DemoFinancialReport',report);
  const minor=(x:string)=>BigInt(x.replace('.',''));expect(minor(report.capturedAmount)-minor(before.capturedAmount)).toBe(minor(q.selection.total.amount));expect(report.integrityIssues).toBe(0);
  await cancelCab(page,b.id);await page.getByRole('button',{name:'Refresh position',exact:true}).click();await expect(gps).toContainText('No position available.');await expect(page.getByRole('button',{name:'Advance simulation stage'})).not.toBeVisible();
});

test('AC-085/089 — capability catalog points to implemented APIs and AWS stays explicitly undeployed',async({page})=>{
  const catalog=await(await page.request.get('/api/v1/demo/catalog')).json();schema('DemoCatalog',catalog);expect(catalog.services).toHaveLength(7);
  for(const service of catalog.services){expect((await page.request.get(service.route)).status()).toBe(200);for(const operation of service.api.split('; ')){const [method,path]=operation.split(' ');expect((contract.paths as Record<string,Record<string,unknown>>)[path]?.[method.toLowerCase()]).toBeTruthy();}}
  const deployment=await(await page.request.get('/api/v1/demo/deployment')).json();schema('DemoDeployment',deployment);expect(deployment.awsDeployed).toBe(false);expect(deployment.awsStatus).toBe('NOT_DEPLOYED');await expect(panel(page,'Dummy deployment target')).toContainText('LOCAL_COMPOSE · NOT_DEPLOYED');
  expect((await page.request.get('/api/v1/demo/gps/'+crypto.randomUUID())).status()).toBe(404);expect((await page.request.get('/api/v1/demo/payments/'+crypto.randomUUID())).status()).toBe(404);
});
