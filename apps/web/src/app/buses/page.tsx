import {Suspense} from "react";
import {BusSearchPage} from "@/features/buses/BusViews";
import {Loading} from "@/components/Feedback";
export default function Page(){return <Suspense fallback={<Loading/>}><BusSearchPage/></Suspense>;}
