import {Suspense} from "react";
import {HotelDetail} from "@/features/hotels/HotelDetail";
import {Loading} from "@/components/Feedback";
export default async function HotelPage({params}:{params:Promise<{id:string}>}){const {id}=await params;return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><HotelDetail id={id}/></Suspense>;}
