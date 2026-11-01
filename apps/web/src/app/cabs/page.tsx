import {Suspense} from "react";
import {CabSearchPage} from "@/features/cabs/CabViews";
import {Loading} from "@/components/Feedback";
export default function Page(){return <Suspense fallback={<Loading/>}><CabSearchPage/></Suspense>;}
