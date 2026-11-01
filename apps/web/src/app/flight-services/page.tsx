import {Suspense} from 'react';
import {FlightServices} from '@/features/flight-services/FlightServices';
import {Loading} from '@/components/Feedback';
export default function Page(){return <Suspense fallback={<Loading/>}><FlightServices/></Suspense>;}
