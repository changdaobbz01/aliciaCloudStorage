const ICP_RECORD_NUMBER = '鄂ICP备2026018755号-2';
const MIIT_RECORDS_URL = 'https://beian.miit.gov.cn';

export function RegulatoryFooter() {
  return (
    <div className="regulatory-footer">
      <span className="regulatory-footer-label">备案信息</span>
      <a
        href={MIIT_RECORDS_URL}
        target="_blank"
        rel="noreferrer"
        className="regulatory-footer-link"
      >
        {ICP_RECORD_NUMBER}
      </a>
    </div>
  );
}
