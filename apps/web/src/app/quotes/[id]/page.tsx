import {Suspense} from "react";
import {QuoteReview} from "@/features/quotes/QuoteReview";
import {Loading} from "@/components/Feedback";
export default function QuotePage(){return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><QuoteReview/></Suspense>;}

