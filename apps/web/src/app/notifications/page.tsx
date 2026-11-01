import {Suspense} from 'react';
import {NotificationPage} from '@/features/notifications/Notifications';
import {Loading} from '@/components/Feedback';
export default function Page(){return <Suspense fallback={<Loading/>}><NotificationPage/></Suspense>;}
