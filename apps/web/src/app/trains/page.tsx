import{Suspense}from"react";import{TrainSearch}from"@/features/trains/TrainSearch";import{Loading}from"@/components/Feedback";
export default function Trains(){return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><TrainSearch/></Suspense>;}
