import { useEffect, useState } from 'react';
import { fetchDriveOverview, fetchHealth, fetchUsageHistory } from '../../../lib/api';
import type { DriveOverview, HealthResponse, UsageHistoryPoint } from '../../../types';

const DEFAULT_HOME_BACKGROUND_ACCENT = {
  eyebrow: '#334155',
  title: '#0f172a',
};

function clamp(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value));
}

function rgbToHsl(red: number, green: number, blue: number) {
  const r = red / 255;
  const g = green / 255;
  const b = blue / 255;
  const max = Math.max(r, g, b);
  const min = Math.min(r, g, b);
  const lightness = (max + min) / 2;
  const delta = max - min;

  if (delta === 0) {
    return { hue: 0, saturation: 0, lightness };
  }

  const saturation = lightness > 0.5 ? delta / (2 - max - min) : delta / (max + min);
  let hue = 0;

  switch (max) {
    case r:
      hue = (g - b) / delta + (g < b ? 6 : 0);
      break;
    case g:
      hue = (b - r) / delta + 2;
      break;
    default:
      hue = (r - g) / delta + 4;
      break;
  }

  return {
    hue: hue * 60,
    saturation,
    lightness,
  };
}

function toAccentColor(
  hue: number,
  saturation: number,
  lightness: number,
  targetLightness: number,
  lightnessRange: { min: number; max: number },
) {
  const normalizedHue = ((hue % 360) + 360) % 360;
  const normalizedSaturation = clamp(saturation, 0.45, 0.88);
  const adjustedLightness = clamp(targetLightness + (lightness - 0.5) * 0.08, lightnessRange.min, lightnessRange.max);

  return `hsl(${Math.round(normalizedHue)} ${Math.round(normalizedSaturation * 100)}% ${Math.round(adjustedLightness * 100)}%)`;
}

async function deriveHomeBackgroundAccent(imageUrl: string) {
  if (typeof window === 'undefined') {
    return DEFAULT_HOME_BACKGROUND_ACCENT;
  }

  return new Promise<typeof DEFAULT_HOME_BACKGROUND_ACCENT>((resolve) => {
    const image = new Image();
    image.decoding = 'async';
    image.crossOrigin = 'anonymous';

    image.onload = () => {
      try {
        const sampleWidth = 64;
        const sampleHeight = 40;
        const canvas = document.createElement('canvas');
        canvas.width = sampleWidth;
        canvas.height = sampleHeight;

        const context = canvas.getContext('2d', { willReadFrequently: true });
        if (!context) {
          resolve(DEFAULT_HOME_BACKGROUND_ACCENT);
          return;
        }

        context.drawImage(image, 0, 0, sampleWidth, sampleHeight);
        const { data } = context.getImageData(0, 0, sampleWidth, sampleHeight);
        const buckets = Array.from({ length: 18 }, () => ({
          weight: 0,
          red: 0,
          green: 0,
          blue: 0,
        }));
        const focusWidth = Math.max(1, Math.round(sampleWidth * 0.36));
        const focusHeight = Math.max(1, Math.round(sampleHeight * 0.38));

        for (let index = 0; index < data.length; index += 4) {
          const alpha = data[index + 3] / 255;
          if (alpha < 0.2) {
            continue;
          }

          const pixelIndex = index / 4;
          const x = pixelIndex % sampleWidth;
          const y = Math.floor(pixelIndex / sampleWidth);
          if (x >= focusWidth || y >= focusHeight) {
            continue;
          }

          const red = data[index];
          const green = data[index + 1];
          const blue = data[index + 2];
          const { hue, saturation, lightness } = rgbToHsl(red, green, blue);

          if (saturation < 0.12 || lightness < 0.08 || lightness > 0.92) {
            continue;
          }

          const hueIndex = Math.floor((((hue % 360) + 360) % 360) / (360 / buckets.length)) % buckets.length;
          const horizontalBias = 1 - x / focusWidth;
          const verticalBias = 1 - y / focusHeight;
          const weight =
            alpha *
            (0.4 + saturation * 1.35) *
            (0.45 + horizontalBias * 0.95) *
            (0.5 + verticalBias * 0.75) *
            (0.25 + clamp(1 - Math.abs(lightness - 0.48) * 1.45, 0.18, 1));

          buckets[hueIndex].weight += weight;
          buckets[hueIndex].red += red * weight;
          buckets[hueIndex].green += green * weight;
          buckets[hueIndex].blue += blue * weight;
        }

        const dominantBucket = buckets.reduce((best, current) => (current.weight > best.weight ? current : best), buckets[0]);

        if (!dominantBucket || dominantBucket.weight <= 0) {
          resolve(DEFAULT_HOME_BACKGROUND_ACCENT);
          return;
        }

        const averageRed = dominantBucket.red / dominantBucket.weight;
        const averageGreen = dominantBucket.green / dominantBucket.weight;
        const averageBlue = dominantBucket.blue / dominantBucket.weight;
        const { hue, saturation, lightness } = rgbToHsl(averageRed, averageGreen, averageBlue);
        const livelySaturation = Math.max(saturation, 0.58);
        const vividSaturation = Math.max(saturation + 0.14, 0.78);

        resolve({
          eyebrow: toAccentColor(hue, livelySaturation, lightness, 0.56, { min: 0.48, max: 0.66 }),
          title: toAccentColor(hue, vividSaturation, lightness, 0.62, { min: 0.54, max: 0.72 }),
        });
      } catch {
        resolve(DEFAULT_HOME_BACKGROUND_ACCENT);
      }
    };

    image.onerror = () => resolve(DEFAULT_HOME_BACKGROUND_ACCENT);
    image.src = imageUrl;
  });
}

type UseDriveDashboardOptions = {
  authToken: string | null;
  isHomeView: boolean;
  homeBackgroundImage: string | null;
};

export function useDriveDashboard({ authToken, isHomeView, homeBackgroundImage }: UseDriveDashboardOptions) {
  const [health, setHealth] = useState<HealthResponse | null>(null);
  const [overview, setOverview] = useState<DriveOverview | null>(null);
  const [usageHistory, setUsageHistory] = useState<UsageHistoryPoint[]>([]);
  const [homeBackgroundAccent, setHomeBackgroundAccent] = useState(DEFAULT_HOME_BACKGROUND_ACCENT);

  async function loadHealth() {
    try {
      setHealth(await fetchHealth());
    } catch {
      setHealth(null);
    }
  }

  async function loadOverview() {
    if (!authToken) {
      setOverview(null);
      return;
    }

    try {
      setOverview(await fetchDriveOverview(authToken));
    } catch {
      setOverview(null);
    }
  }

  async function loadUsageHistory() {
    if (!authToken) {
      setUsageHistory([]);
      return;
    }

    try {
      setUsageHistory(await fetchUsageHistory(authToken, 30));
    } catch {
      setUsageHistory([]);
    }
  }

  async function loadHomeDashboard() {
    await Promise.all([loadHealth(), loadOverview(), loadUsageHistory()]);
  }

  useEffect(() => {
    let cancelled = false;

    if (!homeBackgroundImage) {
      setHomeBackgroundAccent(DEFAULT_HOME_BACKGROUND_ACCENT);
      return () => {
        cancelled = true;
      };
    }

    void deriveHomeBackgroundAccent(homeBackgroundImage).then((nextAccent) => {
      if (!cancelled) {
        setHomeBackgroundAccent(nextAccent);
      }
    });

    return () => {
      cancelled = true;
    };
  }, [homeBackgroundImage]);

  useEffect(() => {
    if (isHomeView) {
      void loadHomeDashboard();
      return;
    }

    void loadOverview();
  }, [authToken, isHomeView]);

  return {
    health,
    overview,
    usageHistory,
    homeBackgroundAccent,
    loadOverview,
    loadHomeDashboard,
  };
}
