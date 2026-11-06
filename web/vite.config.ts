import { defineConfig } from 'vite';
import { statSync } from 'node:fs';
import { resolve } from 'node:path';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
 plugins: [vue(), {
  name: 'cache-fingerprinted-assets',
  transformIndexHtml: {
   order: 'post',
   handler(_html,context) {
    if(!context.bundle)return;
    const chunks=Object.values(context.bundle).filter(chunk=>chunk.type==='chunk');
    const main=chunks.find(chunk=>chunk.name==='main'),detail=chunks.find(chunk=>chunk.name==='MeetingDetail');
    if(!main||!detail)return;
    const styles=(chunk:typeof main)=>[...(chunk.viteMetadata?.importedCss??[])];
    const common=[main.fileName,...styles(main)];
    const routeChunks=[detail,...chunks.filter(chunk=>detail.imports.includes(chunk.fileName)&&!chunk.isEntry)];
    const direct=[...new Set(routeChunks.flatMap(chunk=>[chunk.fileName,...styles(chunk)]))].filter(path=>!common.includes(path));
    // Start the shell on every route and the detail chunk only on direct minutes entries.
    return [{tag:'script',injectTo:'head' as const,children:
     'const assets='+JSON.stringify(common)+';if(/^\\/meeting\\/[a-f0-9-]{36}\\/minutes\\/?$/.test(location.pathname))assets.push(...'+JSON.stringify(direct)+');for(const path of assets){const link=document.createElement("link");if(path.endsWith(".css")){link.rel="preload";link.as="style";}else{link.rel="modulepreload";}link.crossOrigin="anonymous";link.href="/"+path;document.head.append(link);}'}];
   }
  },
  configurePreviewServer(server) {
   const output=resolve(server.config.root,server.config.environments.client!.build.outDir);
   server.middlewares.use((request,response,next)=>{
    const path=request.url?.split('?',1)[0];
    if((request.method==='GET'||request.method==='HEAD')&&path&&
       /^\/assets\/[a-zA-Z0-9_-]+-[a-zA-Z0-9_-]{8}\.(js|css)$/.test(path)&&
       statSync(resolve(output,'.'+path),{throwIfNoEntry:false})?.isFile()){
     response.setHeader('Cache-Control','public, max-age=31536000, immutable');
     response.setHeader('Vary','Accept-Encoding');
    }
    next();
   });
  },
 }],
 server: {
  proxy: {
   '/api': {
    ws: true,
    target: process.env.AMA_API_TARGET ?? 'http://127.0.0.1:8080',
    changeOrigin: false,
   },
   '/auth': {
    target: process.env.AMA_API_TARGET ?? 'http://127.0.0.1:8080',
    changeOrigin: false,
   },
  },
 },
});
