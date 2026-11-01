import {test,expect,type Page} from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
test.use({trace:'off'});test.setTimeout(180_000);
async function inspect(page:Page){await expect(page.locator('main h1')).toBeVisible();await expect(page.getByRole('navigation',{name:'Primary'})).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);expect((await new AxeBuilder({page}).withTags(['wcag2a','wcag2aa','wcag21a','wcag21aa','wcag22aa']).analyze()).violations).toEqual([]);}
test('AC-097 unified public travel design, responsive layout and accessibility',async({page},info)=>{
 for(const [path,name]of [['/','flights'],['/hotels','hotels'],['/trains','trains'],['/buses','buses'],['/holidays','holidays'],['/cabs','cabs']]){
  await page.goto(path);await inspect(page);await expect(page.getByRole('navigation',{name:'Primary'}).locator('[aria-current="page"]')).toHaveCount(1);
  if(path==='/'||path==='/trains'){const input=page.getByRole('combobox',{name:path==='/'?'To':'To station',exact:true});await input.focus();await expect(page.getByRole('listbox')).toBeVisible();expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await input.press('Escape');}
  await page.locator('h1').click();await page.screenshot({path:info.outputPath(name+'.png'),fullPage:true});
 }
});
test('AC-098 account, trips, organizer, seats and operations share the design without bypassing access',async({page},info)=>{
 await page.goto('/account');await page.getByRole('link',{name:'Continue to secure sign-in'}).click();await page.getByLabel('Username or email',{exact:true}).fill('alice');await page.getByLabel('Password',{exact:true}).fill('alice-local-only');await page.getByRole('button',{name:'Sign In',exact:true}).click();await expect(page.getByRole('heading',{name:'You’re signed in'})).toBeVisible();
 for(const [path,name]of [['/account','account'],['/account/travel','organizer'],['/trips','trips'],['/flight-services','seats'],['/journeys/trips','journey-trips'],['/stays/trips','stay-trips'],['/rail-tickets/trips','rail-trips'],['/sandbox','sandbox'],['/operations/reviews','operations']]){
  await page.goto(path);await inspect(page);if(path==='/operations/reviews')await expect(page.getByRole('main').getByRole('alert')).toBeVisible();await page.locator('h1').click();await page.screenshot({path:info.outputPath(name+'.png'),fullPage:true});
 }
});
