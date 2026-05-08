type RegulatoryFooterProps = {
  className?: string;
};

const ICP_RECORD_NUMBER = '鄂ICP备2026018755号-2';
const MIIT_RECORDS_URL = 'https://beian.miit.gov.cn';

export function RegulatoryFooter({ className }: RegulatoryFooterProps) {
  const footerClassName = className ? `regulatory-footer ${className}` : 'regulatory-footer';

  return (
    <div className={footerClassName}>
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
