import { defineConfig,devices } from "@playwright/test";
export default defineConfig({
  testDir:"./tests",fullyParallel:false,workers:1,retries:0,reporter:[["list"],["html",{open:"never"}]],
  use:{baseURL:"http://127.0.0.1:3000",trace:"retain-on-failure"},
  projects:[{name:"desktop",use:{...devices["Desktop Chrome"]}},{name:"mobile",use:{...devices["iPhone 13"],defaultBrowserType:"chromium"}}],
  webServer: process.env.PLAYWRIGHT_EXTERNAL_SERVERS ? undefined : [
    {command:"sh ../../scripts/maven.sh -f ../api/pom.xml spring-boot:run -Dspring-boot.run.profiles=local-oidc",url:"http://127.0.0.1:8080/actuator/health",reuseExistingServer:!process.env.CI,timeout:120_000},
    {command:"npm run dev",url:"http://127.0.0.1:3000",reuseExistingServer:!process.env.CI,timeout:120_000}
  ]
});
