import {Suspense} from "react";
import {BookingStatus} from "@/features/bookings/BookingViews";
import {Loading} from "@/components/Feedback";
export default function BookingPage(){return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><BookingStatus/></Suspense>;}

