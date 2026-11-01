import {Suspense} from "react";
import {NewJourneyPage} from "@/features/journeys/JourneyViews";
export default function Page(){return <Suspense fallback={<main id="main" className="container">Loading journey…</main>}><NewJourneyPage/></Suspense>;}
