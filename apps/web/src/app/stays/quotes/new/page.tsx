import{Loading}from"@/components/Feedback";
import{Suspense}from"react";import{NewStayQuote}from"@/features/stays/StayViews";export default function Page(){return <Suspense fallback={<main id="main" className="container"><Loading label="Loading your hotel workspace…"/></main>}><NewStayQuote/></Suspense>;}
