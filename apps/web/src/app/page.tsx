import { Suspense } from "react";
import { FlightSearch } from "@/features/flights/FlightSearch";
import { Loading } from "@/components/Feedback";
export default function Home() {return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><FlightSearch/></Suspense>;}

