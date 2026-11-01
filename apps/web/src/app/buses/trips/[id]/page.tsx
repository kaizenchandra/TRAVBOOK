import {Suspense} from "react";
import {BusTripPage} from "@/features/buses/BusViews";
import {Loading} from "@/components/Feedback";
export default function Page(){return <Suspense fallback={<Loading/>}><BusTripPage/></Suspense>;}
