import {test,expect,type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import contract from '../../../contracts/openapi.json' with {type:'json'};
import type {components} from '../src/lib/api/schema';

type Profile=components['schemas']['CustomerProfile'];
test.use({trace:'off'});
const ajv=new Ajv2020({strict:false,allErrors:true});addFormats(ajv);
const originals=new WeakMap<Page,Profile>();
function schema(name:string,data:unknown){const validate=ajv.compile({$ref:'#/components/schemas/'+name,components:contract.components});expect(validate(data),JSON.stringify(validate.errors)).toBe(true);}
async function login(page:Page,user='alice'){
  await page.goto('/account');await page.getByRole('link',{name:'Continue to secure sign-in'}).click();
  await page.getByLabel('Username or email',{exact:true}).fill(user);await page.getByLabel('Password',{exact:true}).fill(user+'-local-only');
  await page.getByRole('button',{name:'Sign In',exact:true}).click();await expect(page.getByRole('heading',{name:'Your profile',exact:true})).toBeVisible();
}
async function profile(page:Page):Promise<Profile>{const r=await page.request.get('/api/v1/me/profile');expect(r.status()).toBe(200);const p=await r.json();schema('CustomerProfile',p);return p;}
async function headers(page:Page,key=crypto.randomUUID()){const s=await(await page.request.get('/auth/session')).json();return {'Idempotency-Key':key,[s.csrfHeaderName]:s.csrfToken};}
function body(p:Profile){return {expectedCustomerId:p.customerId,expectedVersion:p.version,displayName:p.displayName,email:p.email,phone:p.phone,consent:true as const};}
test.beforeEach(async({page})=>{await login(page);originals.set(page,await profile(page));});
test.afterEach(async({page})=>{
  const original=originals.get(page);if(!original)return;
  const current=await profile(page);
  expect(current.customerId).toBe(original.customerId);
  const r=await page.request.put('/api/v1/me/profile',{headers:await headers(page),data:{...body(current),displayName:original.displayName,email:original.email,phone:original.phone}});
  expect(r.status()).toBe(200);
});

test('AC-083 — real OIDC and database profile editing persists through logout and re-login',async({page},info)=>{
  const initial=originals.get(page)!;
  await page.getByLabel('Display name',{exact:true}).fill('Profile Test Traveller');
  await page.getByLabel('Contact email (optional)',{exact:true}).fill('traveller@example.test');
  await page.getByLabel('Contact phone (optional)',{exact:true}).fill('+919876543210');
  await expect(page.getByRole('button',{name:'Save profile',exact:true})).toBeDisabled();
  await page.getByRole('checkbox',{name:'I consent to storing these profile details in my account.'}).focus();await page.keyboard.press('Space');
  const response=page.waitForResponse(r=>r.url().endsWith('/api/v1/me/profile')&&r.request().method()==='PUT');
  await page.getByRole('button',{name:'Save profile',exact:true}).press('Enter');schema('CustomerProfileSaved',await(await response).json());
  await expect(page.getByRole('status')).toContainText('Profile saved.');const saved=await profile(page);
  expect(saved.version).toBe(initial.version+1);expect(saved.emailVerified).toBe(false);expect(saved.phoneVerified).toBe(false);
  await page.reload();await expect(page.getByLabel('Display name',{exact:true})).toHaveValue('Profile Test Traveller');
  expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.screenshot({path:info.outputPath('customer-profile.png'),fullPage:true});
  expect(await page.evaluate(()=>JSON.stringify({...localStorage,...sessionStorage}))).not.toContain('traveller@example.test');
  await page.getByRole('button',{name:'Sign out',exact:true}).click();await expect(page.getByText('You have signed out.',{exact:true})).toBeVisible();
  expect((await page.request.get('/api/v1/me/profile')).status()).toBe(401);await expect(page.getByLabel('Display name',{exact:true})).not.toBeVisible();
  await login(page);await expect(page.getByLabel('Contact email (optional)',{exact:true})).toHaveValue('traveller@example.test');
});

test('AC-084 — lost save response retries one operation and a stale edit cannot overwrite a newer profile',async({page})=>{
  const initial=await profile(page);await page.getByLabel('Display name',{exact:true}).fill('One successful save');
  await page.getByRole('checkbox',{name:/I consent to storing/}).check();let attempts=0;const keys:string[]=[];
  await page.route('**/api/v1/me/profile',async route=>{
    if(route.request().method()!=='PUT'){await route.continue();return;}
    attempts++;keys.push(route.request().headers()['idempotency-key']);const r=await route.fetch();expect(r.status()).toBe(200);
    if(attempts===1)await route.abort('failed');else await route.fulfill({response:r});
  });
  await page.getByRole('button',{name:'Save profile',exact:true}).click();await expect(page.getByRole('main').getByRole('alert')).toBeVisible();
  await page.getByRole('button',{name:'Save profile',exact:true}).click();await expect(page.getByRole('status')).toContainText('Your earlier save was received');
  expect(attempts).toBe(2);expect(new Set(keys).size).toBe(1);expect((await profile(page)).version).toBe(initial.version+1);
  await page.unroute('**/api/v1/me/profile');
  await page.getByLabel('Display name',{exact:true}).fill('Unsaved stale edit');
  const current=await profile(page);const r=await page.request.put('/api/v1/me/profile',{headers:await headers(page),data:{...body(current),displayName:'Newer other-tab edit'}});expect(r.status()).toBe(200);
  await page.getByRole('checkbox',{name:/I consent to storing/}).check();await page.getByRole('button',{name:'Save profile',exact:true}).click();
  await expect(page.getByRole('main').getByRole('alert')).toContainText('Your profile changed');expect((await profile(page)).displayName).toBe('Newer other-tab edit');
  await page.getByRole('button',{name:'Reload saved profile',exact:true}).click();await expect(page.getByLabel('Display name',{exact:true})).toHaveValue('Newer other-tab edit');
});

test('AC-084 — CSRF, invalid contacts and owner switching cannot write a foreign profile',async({page,browser})=>{
  const alice=await profile(page);
  expect((await page.request.put('/api/v1/me/profile',{headers:{'Idempotency-Key':crypto.randomUUID()},data:body(alice)})).status()).toBe(403);
  expect((await page.request.put('/api/v1/me/profile',{headers:await headers(page),data:{...body(alice),email:'invalid'}})).status()).toBe(422);
  expect((await page.request.put('/api/v1/me/profile',{headers:await headers(page),data:{...body(alice),consent:false}})).status()).toBe(422);
  const cookie=(await page.context().cookies()).find(c=>c.name==='TRAVBOOK_SESSION')!;
  const context=await browser.newContext({baseURL:'http://127.0.0.1:3000'});
  try{
    const bob=await context.newPage();await login(bob,'bob');const before=await profile(bob);expect(before.customerId).not.toBe(alice.customerId);
    const response=await bob.request.put('/api/v1/me/profile',{headers:await headers(bob),data:body(alice)});expect(response.status()).toBe(409);expect((await response.json()).code).toBe('PROFILE_IDENTITY_CONFLICT');
    await page.getByLabel('Display name',{exact:true}).fill('Must not cross accounts');await page.getByRole('checkbox',{name:/I consent to storing/}).check();
    await page.context().addCookies([(await context.cookies()).find(c=>c.name==='TRAVBOOK_SESSION')!]);
    await page.getByRole('button',{name:'Save profile',exact:true}).click();await expect(page.getByRole('main').getByRole('alert')).toContainText('Your signed-in account changed');
    await expect(page.getByLabel('Display name',{exact:true})).not.toBeVisible();expect(await profile(bob)).toEqual(before);
  }finally{await page.context().addCookies([cookie]);await context.close();}
  expect(await profile(page)).toEqual(alice);
});
