import {Suspense} from "react";
import {NewBooking} from "@/features/bookings/BookingViews";
import {Loading} from "@/components/Feedback";
export default function NewBookingPage(){return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><NewBooking/></Suspense>;}

