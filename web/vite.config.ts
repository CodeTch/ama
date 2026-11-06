import { defineConfig } from 'vite';
import { statSync } from 'node:fs';
import { resolve } from 'node:path';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
 plugins: [vue(), {
  name: 'cache-fingerprinted-assets',
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
