import type { ReactNode } from 'react';

type AliciaModalTitleProps = {
  eyebrow: string;
  children: ReactNode;
};

export function AliciaModalTitle({ eyebrow, children }: AliciaModalTitleProps) {
  return (
    <span className="alicia-modal-title-block">
      <span className="alicia-modal-kicker">{eyebrow}</span>
      <span className="alicia-modal-heading">{children}</span>
    </span>
  );
}
