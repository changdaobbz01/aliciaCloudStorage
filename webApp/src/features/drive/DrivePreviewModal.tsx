import { Alert, Button, Modal, Spin } from 'antd';
import type { StorageNode } from '../../types';
import type { DriveDownloadButtonState, DrivePreviewState } from './types';

type DrivePreviewModalProps = {
  previewState: DrivePreviewState;
  getDownloadButtonState: (item: StorageNode) => DriveDownloadButtonState;
  onClose: () => void;
  onDownloadFile: (item: StorageNode) => void | Promise<void>;
};

function renderPreviewContent(previewState: DrivePreviewState) {
  const previewTarget = previewState.target;
  if (!previewTarget) {
    return null;
  }

  if (previewState.loading) {
    return (
      <div className="loading-box preview-loading-box">
        <Spin size="large" />
      </div>
    );
  }

  if (previewState.error) {
    return <Alert type="error" showIcon message="预览失败" description={previewState.error} />;
  }

  const notice = previewState.note ? (
    <Alert type="info" showIcon message={previewState.note} className="preview-notice" />
  ) : null;

  if (previewState.kind === 'image' && previewState.objectUrl) {
    return (
      <div className="preview-layout">
        {notice}
        <div className="preview-frame">
          <img src={previewState.objectUrl} alt={previewTarget.name} className="preview-image" />
        </div>
      </div>
    );
  }

  if (previewState.kind === 'pdf' && previewState.objectUrl) {
    return (
      <div className="preview-layout">
        {notice}
        <iframe title={previewTarget.name} src={previewState.objectUrl} className="preview-iframe" />
      </div>
    );
  }

  if (previewState.kind === 'video' && previewState.objectUrl) {
    return (
      <div className="preview-layout">
        {notice}
        <video controls className="preview-video" src={previewState.objectUrl} />
      </div>
    );
  }

  if (previewState.kind === 'audio' && previewState.objectUrl) {
    return (
      <div className="preview-layout">
        {notice}
        <audio controls className="preview-audio" src={previewState.objectUrl} />
      </div>
    );
  }

  if (previewState.kind === 'text') {
    return (
      <div className="preview-layout">
        {notice}
        <pre className="preview-text">{previewState.textContent || '文件内容为空。'}</pre>
      </div>
    );
  }

  return (
    <div className="preview-layout">
      {notice || <Alert type="info" showIcon message="当前文件暂不支持在线预览，请直接下载查看。" />}
    </div>
  );
}

export function DrivePreviewModal({
  previewState,
  getDownloadButtonState,
  onClose,
  onDownloadFile,
}: DrivePreviewModalProps) {
  const previewTarget = previewState.target;
  const downloadState = previewTarget ? getDownloadButtonState(previewTarget) : null;

  return (
    <Modal
      title={previewTarget ? `预览：${previewTarget.name}` : '文件预览'}
      open={previewTarget !== null}
      width={960}
      onCancel={onClose}
      destroyOnHidden
      footer={
        previewTarget
          ? [
              <Button
                key="download"
                disabled={downloadState?.busy}
                onClick={() => void onDownloadFile(previewTarget)}
              >
                {downloadState?.task ? downloadState.label : '下载文件'}
              </Button>,
              <Button key="close" type="primary" onClick={onClose}>
                关闭
              </Button>,
            ]
          : null
      }
    >
      {renderPreviewContent(previewState)}
    </Modal>
  );
}
