import type { Metadata } from "next";
import "./globals.css";
import "./journeys.css";
import "./flight-services.css";
import "./travel-design.css";
import { Providers } from "@/components/Providers";
import { Shell } from "@/components/Shell";
export const metadata:Metadata={title:"Travbook — A new horizon",description:"Explore simulated flights, trains, stays, buses, holiday plans and cabs with clear prices and policies."};
export default function RootLayout({children}:{children:React.ReactNode}) {
  return <html lang="en"><body><Providers><Shell>{children}</Shell></Providers></body></html>;
}

