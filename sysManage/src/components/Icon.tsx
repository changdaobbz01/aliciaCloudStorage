import type { LucideIcon } from 'lucide-react';

type IconProps = {
  icon: LucideIcon;
  size?: number;
  className?: string;
  strokeWidth?: number;
};

export function Icon({ icon: Lucide, size = 17, className, strokeWidth = 2.35 }: IconProps) {
  return <Lucide aria-hidden="true" className={className} size={size} strokeWidth={strokeWidth} />;
}
