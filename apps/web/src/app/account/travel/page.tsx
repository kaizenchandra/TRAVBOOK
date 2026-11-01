import {Suspense} from 'react';
import {TravelOrganizer} from '@/features/organizer/Organizer';
import {Loading} from '@/components/Feedback';
export default function Page(){return <Suspense fallback={<Loading/>}><TravelOrganizer/></Suspense>;}
