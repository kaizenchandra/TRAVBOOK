export function money(value:{amount:string;currency:string}) {
  return new Intl.NumberFormat("en-IN",{style:"currency",currency:value.currency,maximumFractionDigits:2}).format(Number(value.amount));
}
export function time(value:string) {
  return new Intl.DateTimeFormat("en-IN",{hour:"2-digit",minute:"2-digit",hour12:false,timeZone:"Asia/Kolkata"}).format(new Date(value));
}
export function date(value:string) {
  return new Intl.DateTimeFormat("en-IN",{day:"numeric",month:"short",year:"numeric",timeZone:"Asia/Kolkata"}).format(new Date(value));
}
export function travelDate(offset=0) {
  const parts=new Intl.DateTimeFormat("en-CA",{year:"numeric",month:"2-digit",day:"2-digit",timeZone:"Asia/Kolkata"}).formatToParts(new Date());
  const part=(type:string)=>parts.find(p=>p.type===type)!.value;
  const value=new Date(`${part("year")}-${part("month")}-${part("day")}T12:00:00Z`);
  value.setUTCDate(value.getUTCDate()+offset);
  return value.toISOString().slice(0,10);
}

