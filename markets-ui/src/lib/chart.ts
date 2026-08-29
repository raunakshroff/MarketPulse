// Ported from the Strata design's hand-rolled SVG chart math (coords/line/areaPath/candles),
// fed real O/H/L/C series instead of the design's mulberry32 random walk.

export type Point = [number, number];

export function coords(series: number[], width: number, height: number, pad = 3): Point[] {
  const n = series.length;
  if (n === 0) {
    return [];
  }
  const min = Math.min(...series);
  const max = Math.max(...series);
  const range = max - min || 1;
  return series.map((v, i) => [
    pad + (n > 1 ? i / (n - 1) : 0) * (width - 2 * pad),
    height - pad - ((v - min) / range) * (height - 2 * pad),
  ]);
}

/** Catmull-Rom-smoothed line path through the given points. */
export function line(pts: Point[]): string {
  if (pts.length === 0) {
    return '';
  }
  if (pts.length < 3) {
    return 'M' + pts.map((p) => `${p[0].toFixed(1)} ${p[1].toFixed(1)}`).join(' L ');
  }
  let d = `M${pts[0][0].toFixed(1)} ${pts[0][1].toFixed(1)}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] || p2;
    const c1x = p1[0] + (p2[0] - p0[0]) / 6;
    const c1y = p1[1] + (p2[1] - p0[1]) / 6;
    const c2x = p2[0] - (p3[0] - p1[0]) / 6;
    const c2y = p2[1] - (p3[1] - p1[1]) / 6;
    d += ` C${c1x.toFixed(1)} ${c1y.toFixed(1)} ${c2x.toFixed(1)} ${c2y.toFixed(1)} ${p2[0].toFixed(1)} ${p2[1].toFixed(1)}`;
  }
  return d;
}

export function areaPath(pts: Point[], height: number): string {
  if (pts.length === 0) {
    return '';
  }
  return `${line(pts)} L${pts[pts.length - 1][0].toFixed(1)} ${height} L${pts[0][0].toFixed(1)} ${height} Z`;
}

export interface Candle {
  cx: number;
  x: number;
  bodyW: number;
  bodyY: number;
  bodyH: number;
  yH: number;
  yL: number;
  up: boolean;
}

/** Real OHLC candles (unlike the design's simulated ones - we have genuine open/high/low/close). */
export function candles(
  ohlc: { open: number; high: number; low: number; close: number }[],
  width: number,
  height: number,
  pad = 4,
): Candle[] {
  if (ohlc.length === 0) {
    return [];
  }
  let min = Infinity;
  let max = -Infinity;
  ohlc.forEach((d) => {
    min = Math.min(min, d.low);
    max = Math.max(max, d.high);
  });
  const range = max - min || 1;
  const mapY = (v: number) => height - pad - ((v - min) / range) * (height - 2 * pad);
  const slot = width / ohlc.length;
  const bodyWidth = slot * 0.58;
  return ohlc.map((d, i) => {
    const cx = i * slot + slot / 2;
    const yOpen = mapY(d.open);
    const yClose = mapY(d.close);
    const up = d.close >= d.open;
    return {
      cx: +cx.toFixed(1),
      x: +(cx - bodyWidth / 2).toFixed(1),
      bodyW: +bodyWidth.toFixed(1),
      bodyY: +Math.min(yOpen, yClose).toFixed(1),
      bodyH: +Math.max(1.5, Math.abs(yOpen - yClose)).toFixed(1),
      yH: +mapY(d.high).toFixed(1),
      yL: +mapY(d.low).toFixed(1),
      up,
    };
  });
}

export function money(n: number, dec = 2): string {
  return (
    '₹' +
    n.toLocaleString('en-IN', { minimumFractionDigits: dec, maximumFractionDigits: dec })
  );
}

export function compact(n: number): string {
  const a = Math.abs(n);
  if (a >= 1e12) return '₹' + (n / 1e12).toFixed(2) + 'T';
  if (a >= 1e9) return '₹' + (n / 1e9).toFixed(2) + 'B';
  if (a >= 1e7) return '₹' + (n / 1e7).toFixed(2) + 'Cr';
  if (a >= 1e5) return '₹' + (n / 1e5).toFixed(2) + 'L';
  return money(n, 0);
}
