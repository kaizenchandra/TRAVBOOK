"use client";
import { useEffect, useId, useState } from "react";
import { MapPin } from "lucide-react";
import { useLocations } from "@/features/flights/hooks";
export function LocationInput({label,value,onChange}:{label:string;value:string;onChange:(value:string)=>void}) {
  const id=useId();
  const [text,setText]=useState(value);
  const [query,setQuery]=useState(value);
  const [open,setOpen]=useState(false);
  const [active,setActive]=useState(-1);
  useEffect(()=>{if(value) setText(value);},[value]);
  useEffect(()=>{const timer=setTimeout(()=>setQuery(text),200);return()=>clearTimeout(timer);},[text]);
  const locations=useLocations(query,open);
  const selected=useLocations(value,!!value);
  const airport=selected.data?.find(location=>location.id===value);
  const options=locations.data ?? [];
  function select(code:string) {onChange(code);setText(code);setOpen(false);setActive(-1);}
  return <div className="location-field" data-open={open}><label htmlFor={id}>{label}</label>{!open&&airport&&<span className="location-city" aria-hidden="true">{airport.city}</span>}<div className="input-icon"><MapPin size={19} aria-hidden="true"/>
    <input id={id} role="combobox" aria-autocomplete="list" aria-expanded={open} aria-controls={id+"-list"}
      aria-activedescendant={open&&active>=0?id+"-"+active:undefined} autoComplete="off" value={text} maxLength={80} placeholder="City or airport" required
      onFocus={()=>setOpen(true)} onBlur={()=>setOpen(false)}
      onChange={e=>{setText(e.target.value);onChange("");setOpen(true);setActive(-1);}}
      onKeyDown={e=>{
        if(e.key==="ArrowDown"){e.preventDefault();setOpen(true);setActive(v=>Math.min(v+1,options.length-1));}
        if(e.key==="ArrowUp"){e.preventDefault();setActive(v=>Math.max(v-1,0));}
        if(e.key==="Escape")setOpen(false);
        if(e.key==="Enter"&&open&&active>=0&&options[active]){e.preventDefault();select(options[active].id);}
      }}/>
    </div>
    {open&&<div className="suggestions"><ul id={id+"-list"} role="listbox" aria-label={label+" airports"}>
      {options.map((l,index)=><li key={l.id} id={id+"-"+index} role="option" aria-selected={active===index} onMouseDown={e=>e.preventDefault()} onClick={()=>select(l.id)}>
        <span><strong>{l.city}</strong><small>{l.name}</small></span><b>{l.id}</b>
      </li>)}
    </ul>{locations.isFetching&&<p role="status">Looking up airports…</p>}
    {locations.isError&&<p role="alert">Airport lookup unavailable. <button type="button" onMouseDown={e=>e.preventDefault()} onClick={()=>locations.refetch()}>Retry</button></p>}
    {!locations.isFetching&&!locations.isError&&options.length===0&&<p>No matching sample airports.</p>}</div>}
  </div>;
}

