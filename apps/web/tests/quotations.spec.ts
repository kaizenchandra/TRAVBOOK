import {test,expect,type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import contract from '../../../contracts/openapi.json' with {type:'json'};
import {travelDate} from '../src/lib/format';
// Auth network traces can contain authorization codes/session cookies. Do not record them.
test.use({trace:'off'});
const ajv=new Ajv2020({strict:false,allErrors:true});addFormats(ajv);
function schema(name:string,data:unknown){const validate=ajv.compile({$ref:'#/components/schemas/'+name,components:contract.components});expect(validate(data),JSON.stringify(validate.errors)).toBe(true);}
async function login(page:Page,user='alice') {
  await page.goto('/account');await expect(page.getByRole('link',{name:'Continue to secure sign-in'})).toBeVisible();
  const before=(await page.context().cookies()).find(c=>c.name==='TRAVBOOK_SESSION');
  await page.getByRole('link',{name:'Continue to secure sign-in'}).click();
  await expect(page).toHaveURL(/127\.0\.0\.1:8180/);
  const url=new URL(page.url());expect(url.searchParams.get('code_challenge_method')).toBe('S256');
  expect(url.searchParams.get('state')).toBeTruthy();expect(url.searchParams.get('nonce')).toBeTruthy();
  await page.getByLabel('Username or email',{exact:true}).fill(user);await page.getByLabel('Password',{exact:true}).fill(user+'-local-only');
  await page.getByRole('button',{name:'Sign In',exact:true}).click();
  await expect(page.getByRole('heading',{name:'You’re signed in'})).toBeVisible();
  const after=(await page.context().cookies()).find(c=>c.name==='TRAVBOOK_SESSION');
  expect(after?.httpOnly).toBe(true);expect(after?.sameSite).toBe('Lax');expect(after?.value).not.toBe(before?.value);
  expect(await page.evaluate(()=>Object.keys(localStorage).concat(Object.keys(sessionStorage)))).toEqual([]);
}
async function flight(page:Page,index=0){
  const response=await page.request.post('/api/v1/flights/search',{data:{origin:'DEL',destination:'BOM',departureDate:travelDate(2),returnDate:null,adults:1,children:0,infants:0,cabin:'ECONOMY'}});
  expect(response.status()).toBe(200);return (await response.json()).offers[index].id as string;
}
async function session(page:Page){const response=await page.request.get('/auth/session');const data=await response.json();schema('Session',data);return data;}
async function create(page:Page,offerId:string,key=crypto.randomUUID()){
  const auth=await session(page);const response=await page.request.post('/api/v1/quotes',{headers:{'Idempotency-Key':key,[auth.csrfHeaderName]:auth.csrfToken},data:{offerId}});
  expect(response.status()).toBe(201);const data=await response.json();schema('Quotation',data);return data;
}
test('AC-008/009 — real OIDC login, private immutable quote, acceptance and logout',async({page},info)=>{
  await login(page);
  await page.goto('/');await page.getByRole('button',{name:'Search flights',exact:true}).click();await expect(page.getByText('8 matching options')).toBeVisible();
  await page.getByRole('link',{name:'View flight'}).nth(2).click();await page.getByRole('link',{name:'Get a personal quote'}).click();
  await page.getByRole('button',{name:'Create my quote'}).click();await expect(page).toHaveURL(/\/quotes\/[0-9a-f-]{36}$/);
  await expect(page.getByText('The price changed.',{exact:true})).toBeVisible();const quoteUrl=page.url();
  await expect(page.getByRole('button',{name:'Accept this quote'})).toBeDisabled();
  await page.getByRole('checkbox',{name:/I accept the quoted total/}).check();await page.getByRole('button',{name:'Accept this quote'}).click();
  await expect(page.getByText('Quote accepted',{exact:true})).toBeVisible();await page.reload();await expect(page.getByText('Quote accepted',{exact:true})).toBeVisible();
  const id=new URL(quoteUrl).pathname.split('/').pop();const response=await page.request.get('/api/v1/quotes/'+id);const details=await response.json();schema('QuoteDetails',details);expect(details.acceptance.quoteVersion).toBe(1);
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:info.outputPath('accepted-quote.png'),fullPage:true});
  await page.goto('/account');await page.getByRole('button',{name:'Sign out',exact:true}).click();await expect(page).toHaveURL(/account\?loggedOut=true/);
  await expect(page.getByText('You have signed out.',{exact:true})).toBeVisible();expect((await page.request.get('/api/v1/quotes/'+id)).status()).toBe(401);
  await page.getByRole('link',{name:'Continue to secure sign-in'}).click();await expect(page.getByLabel('Username or email',{exact:true})).toBeVisible();
});
test('AC-010 — cookie mutations require CSRF and another customer cannot read or accept the quote',async({page,browser})=>{
  await login(page);const offerId=await flight(page);const quote=await create(page,offerId);
  const missing=await page.request.post('/api/v1/quotes',{headers:{'Idempotency-Key':crypto.randomUUID()},data:{offerId}});expect(missing.status()).toBe(403);
  const wrong=await page.request.post('/api/v1/quotes',{headers:{'Idempotency-Key':crypto.randomUUID(),'X-CSRF-TOKEN':'invalid'},data:{offerId}});expect(wrong.status()).toBe(403);
  const context=await browser.newContext({baseURL:'http://127.0.0.1:3000'});try{
    const bob=await context.newPage();await login(bob,'bob');expect((await bob.request.get('/api/v1/quotes/'+quote.id)).status()).toBe(404);
    const auth=await session(bob);const denied=await bob.request.post('/api/v1/quotes/'+quote.id+'/acceptance',{headers:{'Idempotency-Key':crypto.randomUUID(),[auth.csrfHeaderName]:auth.csrfToken},data:{version:1}});expect(denied.status()).toBe(404);
  }finally{await context.close();}
});
test('AC-011 — refresh preserves a pending quote command and session loss hides private data',async({page})=>{
  await login(page);const offerId=await flight(page);const key=crypto.randomUUID();
  const original=await create(page,offerId,key);
  await page.goto('/quotes/new?offerId='+offerId+'&requestId='+key);await page.reload();await page.getByRole('button',{name:'Create my quote'}).click();
  await expect(page).toHaveURL(new RegExp('/quotes/'+original.id+'$'));
  await page.context().clearCookies();await page.reload();await expect(page.getByRole('heading',{name:'Sign in to continue'})).toBeVisible();
  await expect(page.getByText(original.id,{exact:false})).not.toBeVisible();
  await page.getByRole('link',{name:'Continue to secure sign-in'}).click();
  await page.getByLabel('Username or email',{exact:true}).fill('alice');await page.getByLabel('Password',{exact:true}).fill('alice-local-only');
  await page.getByRole('button',{name:'Sign In',exact:true}).click();
  await expect(page).toHaveURL(new RegExp('/quotes/'+original.id+'$'));
  await expect(page.getByText(original.id,{exact:false})).toBeVisible();
});
test('AC-012 — uncorrelated OIDC callback cannot establish a session',async({page})=>{
  const response=await page.request.get('/login/oauth2/code/travbook?code=invalid&state=unmatched',{maxRedirects:0});expect(response.status()).toBe(302);
  expect(response.headers().location).toContain('/account?loginError=true');expect((await session(page)).authenticated).toBe(false);
});
