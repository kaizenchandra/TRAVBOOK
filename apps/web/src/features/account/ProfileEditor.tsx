"use client";
import {useRef,useState} from "react";
import {ErrorNotice,Loading} from "@/components/Feedback";
import {ApiError} from "@/lib/api/client";
import {useProfile,useSaveProfile,type Profile,type ProfileInput} from "./profile-hooks";

export function ProfileEditor({owner}:{owner:string}){
  const query=useProfile(owner),[revision,setRevision]=useState(0),[notice,setNotice]=useState("");
  if(query.isPending)return <Loading label="Loading your saved profile…"/>;
  if(query.isError)return <section aria-label="Profile unavailable"><ErrorNotice error={query.error} retry={()=>query.refetch()}/><button className="button secondary" onClick={()=>window.location.reload()}>Reload account</button></section>;
  return <section aria-labelledby="profile-heading" className="profile-section">
    <h2 id="profile-heading">Your profile</h2><p>These details are saved to your account. Leave an optional field blank to clear it.</p>
    <p className="notice warning">Email and phone are unverified contact details. Saving them does not change your sign-in or enable messages.</p>
    {notice&&<p role="status" className="notice success">{notice}</p>}
    <ProfileForm key={revision} profile={query.data} refreshing={query.isFetching}
      refresh={async()=>{const result=await query.refetch();if(result.isSuccess){setRevision(v=>v+1);setNotice("");}}}
      saved={replayed=>{setRevision(v=>v+1);setNotice(replayed?"Your earlier save was received. The current saved profile is shown.":"Profile saved.");}}/>
  </section>;
}
function ProfileForm({profile,refresh,refreshing,saved}:{profile:Profile;refresh:()=>Promise<void>;refreshing:boolean;saved:(replayed:boolean)=>void}){
  const mutation=useSaveProfile(profile.customerId);
  const[body,setBody]=useState<ProfileInput>({expectedCustomerId:profile.customerId,expectedVersion:profile.version,displayName:profile.displayName,email:profile.email,phone:profile.phone,consent:true});
  const[consent,setConsent]=useState(false);
  const command=useRef<{json:string;key:string}|null>(null);
  const denied=mutation.error instanceof ApiError&&["UNAUTHENTICATED","FORBIDDEN","PROFILE_IDENTITY_CONFLICT"].includes(mutation.error.code);
  const conflict=body.expectedVersion!==profile.version||mutation.error instanceof ApiError&&mutation.error.code==="PROFILE_VERSION_CONFLICT";
  async function submit(event:React.FormEvent){
    event.preventDefault();if(!consent)return;
    const json=JSON.stringify(body);
    if(command.current?.json!==json)command.current={json,key:crypto.randomUUID()};
    try{const result=await mutation.mutateAsync({body,key:command.current!.key});saved(result.replayed);}catch{/* Show the error and preserve the exact command for safe retry. */}
  }
  if(denied)return <><ErrorNotice error={mutation.error!}/><button className="button secondary" onClick={()=>window.location.reload()}>Reload account</button></>;
  return <form onSubmit={submit} aria-label="Edit your profile">
    <fieldset disabled={mutation.isPending} className="profile-fields"><legend className="sr-only">Personal contact details</legend>
      <div className="field"><label htmlFor="profile-name">Display name</label><input id="profile-name" autoComplete="nickname" maxLength={80} value={body.displayName} onChange={e=>setBody({...body,displayName:e.target.value})}/></div>
      <div className="field"><label htmlFor="profile-email">Contact email (optional)</label><input id="profile-email" type="email" autoComplete="email" maxLength={254} value={body.email} onChange={e=>setBody({...body,email:e.target.value})}/></div>
      <div className="field"><label htmlFor="profile-phone">Contact phone (optional)</label><input id="profile-phone" type="tel" autoComplete="tel" maxLength={16} pattern="[+][1-9][0-9]{7,14}" aria-describedby="profile-phone-help" value={body.phone} onChange={e=>setBody({...body,phone:e.target.value})}/><small id="profile-phone-help">International format: + followed by 8–15 digits.</small></div>
      <label className="checkbox profile-consent"><input type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)}/>I consent to storing these profile details in my account.</label>
    </fieldset>
    {mutation.isError&&<ErrorNotice error={mutation.error}/>}
    {conflict&&<p role="status" className="notice warning">A newer profile is saved. Reload the saved profile before editing again; reloading discards this unsaved draft.</p>}
    {mutation.isError&&!conflict&&<p>A save may have reached the server even if its response was lost. Retry the unchanged details or reload the saved profile to check.</p>}
    <div className="profile-actions"><button className="button primary" disabled={!consent||mutation.isPending||conflict}>{mutation.isPending?"Saving profile…":"Save profile"}</button>
      <button type="button" className="button secondary" disabled={mutation.isPending||refreshing} onClick={()=>refresh()}>Reload saved profile</button></div>
    {profile.updatedAt&&<p className="muted">Saved version {profile.version} · {new Date(profile.updatedAt).toLocaleString("en-IN",{day:"numeric",month:"short",year:"numeric",hour:"2-digit",minute:"2-digit",timeZoneName:"short"})}</p>}
  </form>;
}
