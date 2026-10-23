import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

const frontendPort = Number(process.env.FRONTEND_PORT || 5176);
const backendProxy = process.env.VITE_BACKEND_PROXY || `http://localhost:${process.env.BACKEND_PORT || 8085}`;

export default defineConfig({
  plugins: [vue()],
  server: {
    host: '0.0.0.0',
    port: frontendPort,
    allowedHosts: true,
    proxy: {
      '/api': {
        target: backendProxy,
        changeOrigin: true,
      },
    },
  },
});
