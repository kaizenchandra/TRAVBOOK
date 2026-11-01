import {Suspense} from "react";
import {HolidayDetailPage} from "@/features/holidays/HolidayViews";
import {Loading} from "@/components/Feedback";
export default function Page(){return <Suspense fallback={<Loading/>}><HolidayDetailPage/></Suspense>;}
