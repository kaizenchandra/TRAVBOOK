import {Suspense} from "react";
import {HotelSearch} from "@/features/hotels/HotelSearch";
import {Loading} from "@/components/Feedback";
export default function Hotels(){return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><HotelSearch/></Suspense>;}
