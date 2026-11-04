import type {components} from '../../shared/api/generated';
export type WorkspaceVersion=components['schemas']['SavedWorkspaceDetail'];
export const versionCategoryLabels:Record<string,string>={OVERVIEW:'会议概述',DISCUSSION:'关键内容',CONCLUSION:'会议结论',ACTION:'行动项',RISK:'风险与阻碍',OPEN_QUESTION:'待确认事项',DISAGREEMENT:'分歧'};
export function versionFields(version:WorkspaceVersion):Record<string,string>{
 const fields:Record<string,string>={title:version.body.title,body:version.body.body};
 for(const [category,items] of Object.entries(version.elements))fields[category]=items.map(item=>item.body+(item.action?'\n负责人：'+(item.action.owner_display??'未明确')+(item.action.owner_enterprise_id?'（'+item.action.owner_enterprise_id+'）':'')+(item.action.due_on?' · 截止日期：'+item.action.due_on:''):'')).join('\n\n');
 let themes:string[]=[];try{const parsed=JSON.parse(version.template.themes);const values=Array.isArray(parsed)?parsed:parsed.themes;if(Array.isArray(values)&&values.every(x=>typeof x==='string'))themes=values;}catch{/* Older archived formats remain explicitly unavailable. */}
 fields.template=(version.template.name==='Controlled minutes'?'AI生成':version.template.name)+'\n'+(themes.length?themes.map(x=>versionCategoryLabels[x]??x).join('\n'):'该历史模板的主题暂无法读取');
 fields.exclusions=version.exclusions.map(item=>[({INTERNAL_BUSINESS_DATA:'经营数据',POLITICAL_SENSITIVE:'政治敏感'} as Record<string,string>)[item.category]??item.category,item.locator_id,item.reason??''].join(' · ')).sort().join('\n');
 fields.background=version.background.map(item=>item.body).join('\n\n');return fields;
}
export function minutesVersionDiff(left:WorkspaceVersion,right:WorkspaceVersion){const a=versionFields(left),b=versionFields(right),labels:Record<string,string>={title:'标题',body:'正文',...versionCategoryLabels,template:'模板与主题',exclusions:'敏感排除选择',background:'背景材料'};return Object.keys(labels).filter(key=>a[key]!==b[key]).map(key=>({key,label:labels[key]!,before:a[key]??'',after:b[key]??''}));}
