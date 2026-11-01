"use client";
import{useEffect,useId,useState}from"react";
import{MapPin}from"lucide-react";
import{useStations}from"./hooks";
export function StationInput({label,value,onChange}:{label:string;value:string;onChange:(code:string)=>void}){
 const id=useId();const[text,setText]=useState(value),[query,setQuery]=useState(value),[open,setOpen]=useState(false),[active,setActive]=useState(-1);
 useEffect(()=>{if(value)setText(value);},[value]);useEffect(()=>{const timer=setTimeout(()=>setQuery(text),200);return()=>clearTimeout(timer);},[text]);const lookup=useStations(query,open),options=lookup.data??[];
 function select(code:string){onChange(code);setText(code);setOpen(false);setActive(-1);}
 return <div className="location-field"><label htmlFor={id}>{label}</label><div className="input-icon"><MapPin size={18} aria-hidden="true"/><input id={id} role="combobox" aria-autocomplete="list" aria-expanded={open} aria-controls={id+"-list"} aria-activedescendant={open&&active>=0&&options[active]?id+"-"+active:undefined} autoComplete="off" maxLength={80} placeholder="Station name, city or code" required value={text} onFocus={()=>setOpen(true)} onBlur={()=>setOpen(false)} onChange={e=>{setText(e.target.value);onChange("");setActive(-1);setOpen(true);}} onKeyDown={e=>{if(e.key==="ArrowDown"){e.preventDefault();setOpen(true);setActive(n=>Math.min(n+1,options.length-1));}if(e.key==="ArrowUp"){e.preventDefault();setActive(n=>Math.max(n-1,0));}if(e.key==="Escape")setOpen(false);if(e.key==="Enter"&&open&&active>=0&&options[active]){e.preventDefault();select(options[active].code);}}}/></div>
 {open&&<div className="suggestions"><ul id={id+"-list"} role="listbox" aria-label={label+" suggestions"}>{options.map((s,i)=><li id={id+"-"+i} key={s.code} role="option" aria-selected={active===i} onMouseDown={e=>e.preventDefault()} onClick={()=>select(s.code)}><span><strong>{s.name}</strong><small>{s.city}</small></span><b>{s.code}</b></li>)}</ul>{lookup.isFetching&&<p role="status">Finding stations…</p>}{lookup.isError&&<p role="alert">Station lookup failed. <button type="button" onMouseDown={e=>e.preventDefault()} onClick={()=>lookup.refetch()}>Retry</button></p>}{!lookup.isFetching&&!lookup.isError&&!options.length&&<p>No matching sample-catalog stations.</p>}</div>}
 </div>;
}
