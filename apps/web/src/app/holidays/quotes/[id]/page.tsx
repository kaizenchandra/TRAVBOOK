import {Suspense} from "react";
import {HolidayQuotePage} from "@/features/holidays/HolidayViews";
import {Loading} from "@/components/Feedback";
export default function Page(){return <Suspense fallback={<Loading/>}><HolidayQuotePage/></Suspense>;}
