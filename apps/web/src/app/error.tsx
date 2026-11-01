"use client";
export default function ErrorPage({reset}:{reset:()=>void}) {return <main id="main" className="container detail-page"><h1>Something interrupted your journey.</h1><p>Your search can be retried. No reservation or payment was made.</p><button className="button primary" onClick={reset}>Try again</button></main>;}

