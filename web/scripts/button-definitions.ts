import {readdirSync,readFileSync} from 'node:fs';
import {resolve,relative} from 'node:path';
import {parse} from '@vue/compiler-sfc';
import {parse as parseTemplate,NodeTypes,type ElementNode,type TemplateChildNode} from '@vue/compiler-dom';

export type ButtonDefinition={file:string;line:number;column:number;tag:string;role:string|null;label:string;classes:string;ancestors:string[];handler:string;condition:string;variants:string[]};

/** Parse templates rather than grepping lines: branches and repeated controls keep their own source locations. */
export function buttonInventory(root=resolve(import.meta.dirname,'../src')):ButtonDefinition[]{
 function files(dir:string):string[]{return readdirSync(dir,{withFileTypes:true}).flatMap(entry=>entry.isDirectory()?files(resolve(dir,entry.name)):[resolve(dir,entry.name)]);}
 const rows:ButtonDefinition[]=[];
 for(const file of files(root).filter(file=>file.endsWith('.vue')).sort()){
  const {descriptor}=parse(readFileSync(file,'utf8'));
  if(!descriptor.template)continue;
  const template=descriptor.template,ast=parseTemplate(template.content);
  function attr(node:ElementNode,name:string){return node.props.find(prop=>prop.type===NodeTypes.ATTRIBUTE&&prop.name===name);}
  function classes(node:ElementNode){const prop=attr(node,'class');return prop?.type===NodeTypes.ATTRIBUTE?prop.value?.content??'':'';}
  function label(nodes:TemplateChildNode[]):string{return nodes.map(node=>node.type===NodeTypes.TEXT?node.content:node.type===NodeTypes.INTERPOLATION?`{{${node.content.loc.source}}}`:node.type===NodeTypes.ELEMENT?label(node.children):'').join(' ').replace(/\s+/g,' ').trim();}
  function walk(node:TemplateChildNode,parents:ElementNode[]){
   if(node.type!==NodeTypes.ELEMENT)return;
   const role=attr(node,'role'),type=attr(node,'type');
   const dynamicRole=node.props.some(prop=>prop.type===NodeTypes.DIRECTIVE&&prop.name==='bind'&&prop.arg?.type===NodeTypes.SIMPLE_EXPRESSION&&prop.arg.content==='role'&&prop.exp?.loc.source.includes("'button'"));
   const fileLabel=node.tag==='label'&&node.children.some(child=>child.type===NodeTypes.ELEMENT&&child.tag==='input'&&attr(child,'type')?.type===NodeTypes.ATTRIBUTE&&(attr(child,'type') as any).value?.content==='file');
   if(['button','summary','a','RouterLink'].includes(node.tag)||role?.type===NodeTypes.ATTRIBUTE&&role.value?.content==='button'||dynamicRole||fileLabel||node.tag==='input'&&type?.type===NodeTypes.ATTRIBUTE&&['button','submit','reset','file'].includes(type.value?.content??'')){
    const directives=node.props.filter(prop=>prop.type===NodeTypes.DIRECTIVE);
    const aria=attr(node,'aria-label');
    const text=aria?.type===NodeTypes.ATTRIBUTE?aria.value?.content??label(node.children):label(node.children);
    rows.push({file:relative(root,file),line:template.loc.start.line+node.loc.start.line-1,column:node.loc.start.column,tag:node.tag,role:role?.type===NodeTypes.ATTRIBUTE?role.value?.content??null:dynamicRole?'button':null,label:text,classes:classes(node),ancestors:parents.map(classes).filter(Boolean),handler:directives.filter(prop=>prop.type===NodeTypes.DIRECTIVE&&prop.name==='on').map(prop=>prop.exp?.loc.source??'').join('; '),condition:directives.filter(prop=>prop.type===NodeTypes.DIRECTIVE&&['if','else-if','show','bind'].includes(prop.name)).map(prop=>prop.loc.source).join(' '),variants:[...new Set([...node.loc.source.matchAll(/'([^'\n]+)'/g)].map(match=>match[1]!))]});
   }
   for(const child of node.children)walk(child,[...parents,node]);
  }
  for(const node of ast.children)walk(node,[]);
 }
 return rows;
}
