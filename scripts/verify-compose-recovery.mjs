// AC-037: intentionally restarts the local Compose application services, never the data volume.
// Run after the browser suite, not concurrently. Synthetic fixtures only; no network traces or cookies saved.
import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {chromium, expect} from '@playwright/test';

const execute=promisify(execFile);
async function docker(...args) {
  const {stdout}=await execute('docker',args,{timeout:150_000,maxBuffer:2*1024*1024});
  return stdout.trim();
}
const config=JSON.parse(await docker('compose','config','--format','json'));
assert.equal(config.services.backend.environment.SPRING_PROFILES_ACTIVE,'compose-local');
assert.equal(config.services.backend.environment.INTEGRATION_MODE,'MOCK');
for(const service of Object.values(config.services)) {
  for(const port of service.ports??[]) assert.equal(port.host_ip,'127.0.0.1','Fixture ports must stay on loopback');
}
for(const name of ['backend','frontend']) {
  const id=await docker('compose','ps','-q',name);
  assert.ok(id,`${name} must be running`);
  const [container]=JSON.parse(await docker('inspect',id));
  assert.ok(container.Config.User && !['root','0','0:0'].includes(container.Config.User));
  assert.equal(container.HostConfig.ReadonlyRootfs,true);
  assert.equal(container.State.Health.Status,'healthy');
}

const browser=await chromium.launch();
try {
  const context=await browser.newContext({baseURL:'http://127.0.0.1:3000'});
  const page=await context.newPage();
  await page.goto('/account');
  await page.getByRole('link',{name:'Continue to secure sign-in'}).click();
  await page.getByLabel('Username or email',{exact:true}).fill('alice');
  await page.getByLabel('Password',{exact:true}).fill('alice-local-only');
  await page.getByRole('button',{name:'Sign In',exact:true}).click();
  await expect(page.getByRole('heading',{name:'You’re signed in'})).toBeVisible();
  async function headers(key=crypto.randomUUID()) {
    const response=await page.request.get('/auth/session');assert.equal(response.status(),200);
    const s=await response.json();assert.equal(s.authenticated,true);
    return {'Idempotency-Key':key,[s.csrfHeaderName]:s.csrfToken};
  }
  async function post(path,data,status,key) {
    const response=await page.request.post(path,{headers:await headers(key),data});
    assert.equal(response.status(),status,`${path} HTTP status`);return response.json();
  }
  const departureDate=new Date(Date.now()+2*24*60*60*1000).toISOString().slice(0,10);
  const search=await post('/api/v1/flights/search',{origin:'DEL',destination:'BOM',departureDate,returnDate:null,adults:1,children:0,infants:0,cabin:'ECONOMY'},200);
  const quote=await post('/api/v1/quotes',{offerId:search.offers[0].id},201);
  await post(`/api/v1/quotes/${quote.id}/acceptance`,{version:quote.version},200);
  const bookingKey=crypto.randomUUID();
  const input={quoteId:quote.id,quoteVersion:quote.version,travellers:[{givenName:'Alex',familyName:'Example',dateOfBirth:'1990-01-01'}]};
  const booking=await post('/api/v1/bookings',input,202,bookingKey);
  await page.goto(`/bookings/${booking.id}`);
  await expect(page.getByText('Validation complete — awaiting payment',{exact:true})).toBeVisible({timeout:20_000});
  const checkoutKey=crypto.randomUUID();
  const checkout=await post(`/api/v2/bookings/${booking.id}/checkout`,{scenario:'SUCCESS',consent:true},202,checkoutKey);
  await page.reload();
  await expect(page.getByText('MOCK booking confirmed',{exact:true})).toBeVisible({timeout:20_000});
  assert.match(checkout.id,/^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/);
  async function effects() {
    // The UUID was validated above; no untrusted text is interpolated into SQL.
    return docker('compose','exec','-T','postgres','psql','-U','travbook','-d','travbook','-At','-v','ON_ERROR_STOP=1','-c',
      `SELECT (SELECT count(*) FROM mock_supplier_operation WHERE checkout_id='${checkout.id}' AND operation='RESERVE'),
              (SELECT count(*) FROM payment_operation WHERE payment_id='${checkout.id}' AND operation='CAPTURE'),
              (SELECT count(*) FROM payment_journal WHERE payment_id='${checkout.id}'),
              (SELECT sum(CASE WHEN direction='DEBIT' THEN amount ELSE -amount END) FROM payment_journal WHERE payment_id='${checkout.id}')`);
  }
  assert.equal(await effects(),'1|1|2|0.00');
  console.log('AC-037: one MOCK reservation, one capture and balanced entries persisted; restarting application containers.');
  await docker('compose','restart','backend','frontend');
  await docker('compose','up','-d','--wait','--wait-timeout','120');
  await page.reload();
  await expect(page.getByText('MOCK booking confirmed',{exact:true})).toBeVisible();
  assert.equal((await post('/api/v1/bookings',input,202,bookingKey)).id,booking.id);
  assert.equal((await post(`/api/v2/bookings/${booking.id}/checkout`,{scenario:'SUCCESS',consent:true},202,checkoutKey)).id,checkout.id);
  assert.equal(await effects(),'1|1|2|0.00');
  await page.goto('/trips');
  await expect(page.locator(`a[href="/bookings/${booking.id}"]`)).toBeVisible();
  console.log('AC-037 passed: JDBC session, owned booking, My Trips and idempotency survived restart; external MOCK effect counts unchanged.');
} finally {
  await browser.close();
}
