import {Suspense} from "react";
import {HolidaySearchPage} from "@/features/holidays/HolidayViews";
import {Loading} from "@/components/Feedback";
export default function Page(){return <Suspense fallback={<Loading/>}><HolidaySearchPage/></Suspense>;}
