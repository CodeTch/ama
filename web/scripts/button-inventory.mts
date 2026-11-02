import {mkdir,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {buttonInventory} from '../tests/support/button-inventory';

const root=resolve(import.meta.dirname,'../..'),rows=buttonInventory(resolve(root,'web/src'));
const output=resolve(root,'docs/acceptance/b/button-inventory-2026-11-02.md');
const escape=(value:string)=>value.replaceAll('|','&#124;').replaceAll('\n',' ').replaceAll('`','&#96;');
const files=[...new Set(rows.map(row=>row.file))];
let markdown=`# 全页面按钮定义清单（2026-11-02）\n\n从 Vue 模板 AST 生成，包含 ${files.length} 个组件中的 ${rows.filter(row=>row.tag==='button').length} 处原生按钮，以及链接、展开控件、角色按钮和文件选择入口，共 ${rows.length} 处定义。循环生成的按钮以定义位置对账；行号相同的按钮通过列号区分。未注册到路由的遗留组件也保留在清单中。\n\n此文件记录源码覆盖范围；运行时点击、禁用状态和尺寸记录由 Playwright 的 \`button-clicks.json\` 提供。全量定义的实际计算样式由 \`button-stability.spec.ts\` 检查。两者分别保留，不把样式检查记作业务操作成功。\n\n重新生成：在项目根目录运行 \`web/node_modules/.bin/tsx web/scripts/button-inventory.mts\`。\n`;
for(const file of files){
 const controls=rows.filter(row=>row.file===file);
 markdown+=`\n## ${file}\n\n| 位置（行:列） | 元素 | 名称 / 文案表达式 | 显隐与绑定条件 |\n| --- | --- | --- | --- |\n`;
 for(const row of controls)markdown+=`| [${row.line}:${row.column}](../../../web/src/${file}#L${row.line}) | ${row.tag}${row.role?` (${row.role})`:''} | ${escape(row.label)||'图标 / 文件入口'} | ${escape(row.condition)} |\n`;
}
await mkdir(resolve(output,'..'),{recursive:true});await writeFile(output,markdown);
console.log(JSON.stringify({output,components:files.length,nativeButtons:rows.filter(row=>row.tag==='button').length,totalControls:rows.length}));
