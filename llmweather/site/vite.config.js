import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// BASE_PATH is set by the GitHub Pages workflow (e.g. /LLMWeather/). Locally the site serves from /.
export default defineConfig({
  base: process.env.BASE_PATH || '/',
  plugins: [vue()],
})
