import { Button, Result } from 'antd';
import { Component } from 'react';
import type { ErrorInfo, ReactNode } from 'react';

type LazyChunkErrorBoundaryProps = {
  children: ReactNode;
};

type LazyChunkErrorBoundaryState = {
  error: Error | null;
};

function isLazyChunkError(error: Error): boolean {
  return /dynamically imported module|module script|MIME type|ChunkLoadError|Loading chunk/i.test(error.message);
}

export class LazyChunkErrorBoundary extends Component<LazyChunkErrorBoundaryProps, LazyChunkErrorBoundaryState> {
  state: LazyChunkErrorBoundaryState = {
    error: null,
  };

  static getDerivedStateFromError(error: Error): LazyChunkErrorBoundaryState {
    return { error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error('Lazy view failed to load.', error, errorInfo);
  }

  render() {
    const { error } = this.state;

    if (!error) {
      return this.props.children;
    }

    const chunkError = isLazyChunkError(error);

    return (
      <div className="content-panel">
        <Result
          status="warning"
          title={chunkError ? '页面资源已更新' : '页面加载失败'}
          subTitle={
            chunkError
              ? '当前浏览器仍在使用旧的前端资源，请刷新页面加载最新版本。'
              : '页面模块加载失败，请刷新后重试。'
          }
          extra={
            <Button type="primary" onClick={() => window.location.reload()}>
              刷新页面
            </Button>
          }
        />
      </div>
    );
  }
}
