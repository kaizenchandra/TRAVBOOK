import {Suspense} from "react";
import {JourneyBookingPage} from "@/features/journeys/JourneyViews";
export default function Page(){return <Suspense fallback={<main id="main" className="container">Loading journey…</main>}><JourneyBookingPage/></Suspense>;}
