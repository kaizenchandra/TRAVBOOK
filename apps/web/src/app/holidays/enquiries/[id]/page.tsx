import {Suspense} from "react";
import {HolidayEnquiryPage} from "@/features/holidays/HolidayViews";
import {Loading} from "@/components/Feedback";
export default function Page(){return <Suspense fallback={<Loading/>}><HolidayEnquiryPage/></Suspense>;}
