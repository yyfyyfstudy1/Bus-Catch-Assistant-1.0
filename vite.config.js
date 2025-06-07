import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [ vue(), vueDevTools() ],

  resolve:{
    alias:{ '@': fileURLToPath(new URL('./src', import.meta.url)) }
  },

  // 👇 线上放 Vercel，就保持根路径即可
  base: '/',

  server:{
    // 仅本地开发用，线上静态站不会走这里
    proxy:{
      '/api':{
        target:'https://api.transport.nsw.gov.au',
        changeOrigin:true,
        rewrite:path=>path.replace(/^\/api/, '')
      }
    }
  },

  build:{
    outDir:'dist',
    assetsDir:'assets',
    rollupOptions:{
      output:{ manualChunks:{ vendor:['vue'] } }
    }
  }
})

