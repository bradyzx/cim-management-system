import { defineConfig } from '@vben/vite-config';

export default defineConfig(async () => {
  return {
    application: {},
    vite: {
      server: {
        proxy: {
          '/api': {
            changeOrigin: true,
            rewrite: (path) => path.replace(/^\/api/, ''),
            // 真实后端（Spring Boot，context-path=/api）：rewrite 去掉前缀后再由 target 补回 /api
            target: 'http://localhost:8081/api',
            ws: true,
          },
        },
      },
    },
  };
});
