import {Suspense} from "react";
import {Account} from "@/features/account/Account";
import {Loading} from "@/components/Feedback";
export default function AccountPage(){return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><Account/></Suspense>;}

