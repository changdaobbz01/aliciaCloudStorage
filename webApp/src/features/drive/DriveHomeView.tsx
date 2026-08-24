import { StatusPanel } from '../../components/StatusPanel';
import type { DriveOverview, HealthResponse, UsageHistoryPoint } from '../../types';

type DriveHomeViewProps = {
  health: HealthResponse | null;
  overview: DriveOverview | null;
  usageHistory: UsageHistoryPoint[];
  backgroundImage: string | null;
  onChooseBackground: () => void;
  onClearBackground: () => void;
};

export default function DriveHomeView({
  health,
  overview,
  usageHistory,
  backgroundImage,
  onChooseBackground,
  onClearBackground,
}: DriveHomeViewProps) {
  return (
    <div className="home-view-shell">
      <StatusPanel
        health={health}
        overview={overview}
        usageHistory={usageHistory}
        backgroundImage={backgroundImage}
        onChooseBackground={onChooseBackground}
        onClearBackground={onClearBackground}
      />
    </div>
  );
}
