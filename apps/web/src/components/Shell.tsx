"use client";
import Link from "next/link";
import {usePathname} from "next/navigation";
import { Compass, Plane, ShieldCheck, Armchair, BriefcaseBusiness, UserRound,Hotel,TrainFront,BusFront,Palmtree,Car,Heart,ChevronRight } from "lucide-react";
const services=[{name:"Flights",label:"Explore flights",href:"/",icon:Plane},{name:"Hotels",href:"/hotels",icon:Hotel},{name:"Trains",href:"/trains",icon:TrainFront},{name:"Buses",href:"/buses",icon:BusFront},{name:"Holidays",href:"/holidays",icon:Palmtree},{name:"Cabs",href:"/cabs",icon:Car},{name:"Seats & extras",href:"/flight-services",icon:Armchair}];
export function Shell({children}:{children:React.ReactNode}) {
  const path=usePathname();
  const section=path.startsWith('/stays')?'Hotels':path.startsWith('/rail-tickets')?'Trains':services.find(s=>s.href!=='/'&&path.startsWith(s.href))?.name??(path==='/'||path.startsWith('/flights')||path.startsWith('/quotes')||path.startsWith('/bookings')?'Flights':'');
  return <div className="travel-shell" data-section={section}>
    <a className="skip-link" href="#main">Skip to main content</a>
    <header className="site-header"><div className="container header-row">
      <Link href="/" className="brand" aria-label="Travbook home"><span className="brand-mark"><Compass size={25}/></span>travbook<span className="brand-dot">.</span></Link>
      <div className="utility-links"><Link className="utility-link" href="/account/travel"><Heart size={24}/><span><strong>Saved travel</strong><small>Your favourites & travellers</small></span></Link><Link className="utility-link" href="/trips" aria-label="My Trips"><BriefcaseBusiness size={24}/><span><strong>My Trips</strong><small>Manage your bookings</small></span></Link></div>
      <Link className="account-link" href="/account" aria-label="Login / My account"><UserRound size={19}/><span>Login / My account</span><ChevronRight size={15}/></Link>
      <span className="market-label">INDIA <span>•</span> INR</span>
    </div></header>
    <div className="demo-strip"><ShieldCheck size={15}/><span>Explore with simulated fares. No real reservations, tickets or payments.</span></div>
    <nav className="container primary-services" aria-label="Primary">{services.map(({name,label,href,icon:Icon})=><Link href={href} key={href} aria-label={label??name} aria-current={section===name?'page':undefined}><Icon size={33} strokeWidth={1.6}/><span>{name}</span></Link>)}</nav>
    {children}
    <nav className="mobile-nav" aria-label="Mobile"><Link href="/"><Compass size={20}/>Explore</Link><Link href="/flight-services"><Armchair size={20}/>Flight seats</Link><Link href="/trips" aria-label="Open My Trips"><BriefcaseBusiness size={20}/>My Trips</Link><Link href="/account" aria-label="Open account"><UserRound size={20}/>Account</Link></nav>
    <footer className="site-footer"><div className="container footer-grid"><div><Link href="/" className="brand small"><Compass size={24}/> travbook.</Link><p>One place for your next journey.<br/>Search, compare and manage your travel.</p><span className="footer-mode">INDEPENDENT PLATFORM · MOCK EXPERIENCE</span></div><div><h2>Explore travel</h2><Link href="/">Flight searches</Link><Link href="/hotels">Hotels & homestays</Link><Link href="/holidays">Holiday packages</Link></div><div><h2>On the move</h2><Link href="/trains">Train journeys</Link><Link href="/buses">Bus tickets</Link><Link href="/cabs">Cabs & transfers</Link></div><div><h2>Your travel space</h2><Link href="/trips">Manage trips</Link><Link href="/account/travel">Saved details</Link><Link href="/account">Your account</Link></div></div><div className="container footer-bottom"><p>All inventory, payments and travel documents are simulated. No real booking is made.</p><span>Made for clearer journeys.</span></div></footer>
  </div>;
}

