import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
 plugins: [vue()],
 server: {
  proxy: {
   '/api': {
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
