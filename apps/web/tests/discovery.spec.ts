import {test,expect,type Page} from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
async function search(page:Page) {
  await page.goto("/");await page.getByRole("button",{name:"Search flights",exact:true}).click();
  await expect(page.getByText("8 matching options")).toBeVisible();
}
test("AC-001/002/003/007 — search, filter, detail, reprice and refresh on a real API",async({page},testInfo)=>{
  await search(page);
  await page.getByLabel("Non-stop only").check();
  await expect(page.getByText("4 matching options")).toBeVisible();
  await page.getByLabel("Sort by").selectOption("DURATION");
  await expect(page).toHaveURL(/sort=DURATION/);
  const resultsUrl=page.url();
  await page.getByRole("link",{name:"View flight"}).first().click();
  await expect(page.getByRole("heading",{name:"Your itinerary"})).toBeVisible();
  await page.getByRole("button",{name:"Check current fare"}).click();
  await expect(page.getByText("The fare is unchanged.")).toBeVisible();
  await expect(page.getByText("No real money or valid travel ticket is issued.",{exact:false})).toBeVisible();
  await page.getByRole("link",{name:"Back to results"}).click();
  await expect(page).toHaveURL(resultsUrl);await page.reload();
  await expect(page.getByLabel("Non-stop only")).toBeChecked();
  await expect(page.getByText("4 matching options")).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  expect((await new AxeBuilder({page}).withTags(["wcag2a","wcag2aa","wcag21aa","wcag22aa"]).analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath("results.png"),fullPage:true});
});
test("AC-001/007 — keyboard airport lookup, swap, return journey and homepage accessibility",async({page},testInfo)=>{
  await page.goto("/");
  expect((await new AxeBuilder({page}).withTags(["wcag2a","wcag2aa","wcag21aa","wcag22aa"]).analyze()).violations).toEqual([]);
  await page.screenshot({path:testInfo.outputPath("homepage.png"),fullPage:true});
  const from=page.getByRole("combobox",{name:"From",exact:true});
  await from.fill("Bengaluru");await expect(page.getByRole("option",{name:/Bengaluru/})).toBeVisible();
  await from.press("ArrowDown");await from.press("Enter");await expect(from).toHaveValue("BLR");
  await page.getByRole("button",{name:"Swap origin and destination"}).click();await expect(from).toHaveValue("BOM");
  await page.getByLabel("Round trip",{exact:true}).check();
  await page.getByRole("button",{name:"Search flights",exact:true}).click();await expect(page.getByText("6 matching options")).toBeVisible();
  await page.getByRole("link",{name:"View flight"}).first().click();await expect(page.getByText("RETURN",{exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
  expect((await new AxeBuilder({page}).withTags(["wcag2a","wcag2aa","wcag21aa","wcag22aa"]).analyze()).violations).toEqual([]);
});
test("AC-004 — partial provider outage and empty inventory are honest",async({page})=>{
  await page.goto("/");await page.getByRole("button",{name:/FROM NEW DELHI Goa/}).click();
  await page.getByRole("button",{name:"Search flights",exact:true}).click();
  await expect(page.getByText("Some providers are unavailable.")).toBeVisible();await expect(page.getByText("4 matching options")).toBeVisible();
  const to=page.getByRole("combobox",{name:"To",exact:true});await to.fill("Jaipur");
  await expect(page.getByRole("option",{name:/Jaipur/})).toBeVisible();await to.press("ArrowDown");await to.press("Enter");
  await page.getByRole("button",{name:"Search flights",exact:true}).click();await expect(page.getByRole("heading",{name:"No matching flights"})).toBeVisible();
  await expect(page.getByText("Some providers are unavailable.")).not.toBeVisible();
});
test("AC-005 — changed price requires acknowledgement and sold-out is a recoverable conflict",async({page})=>{
  await search(page);
  await page.getByRole("link",{name:"View flight"}).nth(2).click();await page.getByRole("button",{name:"Check current fare"}).click();
  await expect(page.getByText("The fare has changed.")).toBeVisible();await page.getByRole("button",{name:"Acknowledge updated fare"}).click();
  await expect(page.getByText("Updated fare acknowledged for this preview.")).toBeVisible();
  await page.getByRole("link",{name:"Back to results"}).click();await page.getByRole("button",{name:"Next",exact:true}).click();
  await page.getByRole("link",{name:"View flight"}).last().click();await page.getByRole("button",{name:"Check current fare"}).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("no longer available");
});
test("AC-006 — invalid route is explained without losing input",async({page})=>{
  await page.goto("/");const to=page.getByRole("combobox",{name:"To",exact:true});await to.fill("Delhi");
  await expect(page.getByRole("option",{name:/New Delhi/})).toBeVisible();await to.press("ArrowDown");await to.press("Enter");
  await page.getByRole("button",{name:"Search flights",exact:true}).click();await expect(page.getByRole("main").getByRole("alert")).toContainText("different airports");
  await expect(to).toHaveValue("DEL");
});
test("AC-007 — complete search, filter, detail and fare check using only the keyboard",async({page})=>{
  await page.goto("/");
  async function tabTo(target:ReturnType<Page["getByRole"]>) {
    for(let attempt=0;attempt<40;attempt++) {
      if(await target.evaluate(element=>element===document.activeElement))return;
      await page.keyboard.press("Tab");
    }
    throw new Error("Control not reachable in keyboard tab order");
  }
  await tabTo(page.getByRole("button",{name:"Search flights",exact:true}));await page.keyboard.press("Enter");
  await expect(page.getByText("8 matching options")).toBeVisible();
  await tabTo(page.getByRole("checkbox",{name:"Non-stop only"}));await page.keyboard.press("Space");
  await expect(page.getByText("4 matching options")).toBeVisible();
  await tabTo(page.getByRole("link",{name:"View flight"}).first());await page.keyboard.press("Enter");
  await expect(page.getByRole("heading",{name:"Your itinerary"})).toBeVisible();
  await tabTo(page.getByRole("button",{name:"Check current fare"}));await page.keyboard.press("Enter");
  await expect(page.getByText("The fare is unchanged.")).toBeVisible();
});
