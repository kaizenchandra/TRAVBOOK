import type { NextConfig } from "next";
import path from "node:path";
const config: NextConfig = {
  output: "standalone",
  outputFileTracingRoot: path.join(__dirname, "../.."),
  poweredByHeader: false,
  async rewrites() { return ["api","auth","oauth2","login"].map(prefix=>({ source: `/${prefix}/:path*`, destination: `${process.env.API_URL ?? "http://127.0.0.1:8080"}/${prefix}/:path*` })); },
  async headers() { return [{source:"/:path*",headers:[
    {key:"X-Content-Type-Options",value:"nosniff"},
    {key:"Referrer-Policy",value:"strict-origin-when-cross-origin"},
    {key:"Permissions-Policy",value:"camera=(), microphone=(), geolocation=()"},
    {key:"X-Frame-Options",value:"DENY"}
  ]}]; }
};
export default config;

