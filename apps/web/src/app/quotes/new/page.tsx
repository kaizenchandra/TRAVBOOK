import {Suspense} from "react";
import {NewQuote} from "@/features/quotes/QuoteReview";
import {Loading} from "@/components/Feedback";
export default function NewQuotePage(){return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><NewQuote/></Suspense>;}

