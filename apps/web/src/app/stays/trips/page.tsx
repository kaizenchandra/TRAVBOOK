import{Loading}from"@/components/Feedback";
import{Suspense}from"react";import{StayTripsPage}from"@/features/stays/StayViews";export default function Page(){return <Suspense fallback={<main id="main" className="container"><Loading label="Loading your hotel workspace…"/></main>}><StayTripsPage/></Suspense>;}
