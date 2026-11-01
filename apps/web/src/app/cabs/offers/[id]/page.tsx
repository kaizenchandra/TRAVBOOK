import {Suspense} from "react";
import {CabDetailPage} from "@/features/cabs/CabViews";
import {Loading} from "@/components/Feedback";
export default function Page(){return <Suspense fallback={<Loading/>}><CabDetailPage/></Suspense>;}
