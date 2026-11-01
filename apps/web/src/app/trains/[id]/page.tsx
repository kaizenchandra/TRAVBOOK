import{Suspense}from"react";import{TrainDetail}from"@/features/trains/TrainDetail";import{Loading}from"@/components/Feedback";
export default async function TrainPage({params}:{params:Promise<{id:string}>}){const{id}=await params;return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><TrainDetail id={id}/></Suspense>;}
