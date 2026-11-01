import {Suspense} from "react";
import {Sandbox} from "@/features/simulation/Sandbox";
import {Loading} from "@/components/Feedback";
export default function SandboxPage(){return <Suspense fallback={<main id="main" className="container"><Loading/></main>}><Sandbox/></Suspense>;}
