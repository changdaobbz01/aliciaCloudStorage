import { Alert } from 'antd';
import { AppPackagePanel } from '../../components/AppPackagePanel';
import type { AppPackageInfo } from '../../types';

type DriveAppPackageViewProps = {
  isAdmin: boolean;
  packageInfo: AppPackageInfo | null;
  loading: boolean;
  uploading: boolean;
  onUploadClick: () => void;
  onDeletePackage: () => void;
};

export default function DriveAppPackageView({
  isAdmin,
  packageInfo,
  loading,
  uploading,
  onUploadClick,
  onDeletePackage,
}: DriveAppPackageViewProps) {
  if (!isAdmin) {
    return (
      <section className="content-panel">
        <Alert
          type="warning"
          showIcon
          message="当前账号没有 APP 上传权限"
          description="仅全局管理员或云盘管理员可以上传和替换安卓安装包。"
        />
      </section>
    );
  }

  return (
    <AppPackagePanel
      packageInfo={packageInfo}
      loading={loading}
      uploading={uploading}
      onUploadClick={onUploadClick}
      onDeletePackage={onDeletePackage}
    />
  );
}
