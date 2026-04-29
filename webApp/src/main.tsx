import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { App, ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import RootApp from './App';
import { SessionProvider } from './context/session-context';
import './index.css';

/**
 * 挂载前端应用根节点，并注入路由、主题和全局会话能力。
 */
ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#2563eb',
          borderRadius: 8,
          colorBgLayout: '#f5f7fb',
          fontFamily:
            '"PingFang SC", "Hiragino Sans GB", "SF Pro SC", "Microsoft YaHei UI", "Microsoft YaHei", -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif',
        },
      }}
    >
      <App>
        <BrowserRouter>
          <SessionProvider>
            <RootApp />
          </SessionProvider>
        </BrowserRouter>
      </App>
    </ConfigProvider>
  </React.StrictMode>,
);
