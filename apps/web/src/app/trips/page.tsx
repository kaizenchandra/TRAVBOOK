import {Suspense} from "react";
import {MyTrips} from "@/features/bookings/BookingViews";
import {Loading} from "@/components/Feedback";
export default function TripsPage(){return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><MyTrips/></Suspense>;}

