import {useSession} from './shared/composables/useSession';
import {createMinutesEntry} from './shared/api/minutes-entry';
import {DEMO_MEETING_ID} from './features/meetings/meeting-prototype-demo';

// The protected route validates the session before requesting any meeting content.
const match=/^\/meeting\/([a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12})\/minutes\/?$/.exec(location.pathname);
export const directMeeting=match?.[1]!==DEMO_MEETING_ID?match?.[1]:undefined;
export const startupSession=useSession(location.origin);
export const startupEntry=createMinutesEntry(startupSession);
export const initialSession=directMeeting?startupSession.refresh().then(()=>{
 startupEntry.preloadMinutesEntry(directMeeting);
 return true;
},()=>false):undefined;
