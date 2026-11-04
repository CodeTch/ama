import {computed,shallowRef,shallowReadonly,watch} from 'vue';
import type {createMeetingOptionsApi,Person} from '../api/meeting-options';

/** Application-owned identity display: page and header lifetimes never reload it. */
export function createCurrentUserProfile(actor:()=>string|null,people:ReturnType<typeof createMeetingOptionsApi>['people']){
 const profile=shallowRef<Person|null>(null),loading=shallowRef(false);
 const stop=watch(actor,(id,_previous,cleanup)=>{
  profile.value=null;loading.value=!!id;if(!id)return;
  const request=new AbortController();cleanup(()=>request.abort());
  void people(id,null,request.signal).then(value=>{
   if(!request.signal.aborted&&actor()===id)profile.value=value.items.find(person=>person.stableId===id)??null;
  }).catch(()=>{}).finally(()=>{
   if(!request.signal.aborted&&actor()===id)loading.value=false;
  });
 },{immediate:true,flush:'sync'});
 const name=computed(()=>profile.value?.displayName||actor()||'尚未登录');
 return {name,loading:shallowReadonly(loading),dispose:stop};
}
