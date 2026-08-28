import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

const antdChunkGroups: Array<[string, string[]]> = [
  ['antd-table', ['table', 'pagination', 'empty']],
  ['antd-form', ['form']],
  ['antd-modal', ['modal', 'popconfirm']],
  ['antd-select', ['select', 'tree-select']],
  ['antd-input', ['input', 'input-number']],
  ['antd-layout', ['layout', 'menu']],
  ['antd-feedback', ['alert', 'message', 'result', 'spin']],
  ['antd-controls', ['button', 'switch', 'checkbox', 'radio', 'segmented']],
  ['antd-display', ['avatar', 'badge', 'card', 'tag', 'typography', 'space']],
  ['antd-overlay', ['dropdown', 'tooltip', 'popover']],
  ['antd-progress', ['progress', 'qrcode']],
  ['antd-navigation', ['breadcrumb']],
];

function sanitizeChunkName(value: string) {
  return value.replace(/^@/, '').replace(/[^a-z0-9_-]+/gi, '-').toLowerCase();
}

function getAntdModuleChunk(id: string) {
  const marker = '/node_modules/antd/es/';
  const markerIndex = id.indexOf(marker);

  if (markerIndex === -1) {
    return null;
  }

  const segment = id.slice(markerIndex + marker.length).split('/')[0];

  if (!segment) {
    return 'antd-core';
  }

  const group = antdChunkGroups.find(([, segments]) => segments.includes(segment));

  if (group) {
    return group[0];
  }

  if (segment === 'style') {
    return 'antd-style';
  }

  if (segment.startsWith('_')) {
    return 'antd-internal';
  }

  return `antd-${sanitizeChunkName(segment)}`;
}

function getAntdSupportChunk(id: string) {
  const marker = '/node_modules/';
  const markerIndex = id.indexOf(marker);

  if (markerIndex === -1) {
    return null;
  }

  const packagePath = id.slice(markerIndex + marker.length);

  if (packagePath.startsWith('@rc-component/')) {
    return `antd-rc-${sanitizeChunkName(packagePath.split('/')[1] ?? 'component')}`;
  }

  if (packagePath.startsWith('rc-')) {
    return `antd-${sanitizeChunkName(packagePath.split('/')[0])}`;
  }

  if (packagePath.startsWith('@ant-design/icons')) {
    return 'antd-icons';
  }

  if (packagePath.startsWith('@ant-design/cssinjs')) {
    return 'antd-cssinjs';
  }

  if (packagePath.startsWith('@ant-design/')) {
    return `antd-support-${sanitizeChunkName(packagePath.split('/')[1] ?? 'core')}`;
  }

  return null;
}

export default defineConfig({
  base: '/cloudPan/',
  plugins: [react()],
  build: {
    rollupOptions: {
      output: {
        manualChunks(moduleId) {
          const id = moduleId.replaceAll('\\', '/');

          if (id.includes('/node_modules/react/') || id.includes('/node_modules/react-dom/')) {
            return 'react';
          }

          if (id.includes('/node_modules/lucide-react/')) {
            return 'icons';
          }

          const antdModuleChunk = getAntdModuleChunk(id);

          if (antdModuleChunk) {
            return antdModuleChunk;
          }

          const antdSupportChunk = getAntdSupportChunk(id);

          if (antdSupportChunk) {
            return antdSupportChunk;
          }

          if (id.includes('/node_modules/antd/')) {
            return 'antd-core';
          }

          return undefined;
        },
      },
    },
  },
  server: {
    host: '0.0.0.0',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true,
      },
    },
  },
});
