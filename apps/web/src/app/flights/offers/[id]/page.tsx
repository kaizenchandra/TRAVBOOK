import { Suspense } from "react";
import { FlightDetail } from "@/features/flights/FlightDetail";
import { Loading } from "@/components/Feedback";
export default function OfferPage() {return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><FlightDetail/></Suspense>;}

