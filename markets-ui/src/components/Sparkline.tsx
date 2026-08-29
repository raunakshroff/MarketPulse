import { coords, line } from '../lib/chart';

export default function Sparkline({
  series,
  width = 132,
  height = 34,
  color,
}: {
  series: number[];
  width?: number;
  height?: number;
  color: string;
}) {
  const pts = coords(series, width, height, 3);
  return (
    <svg width={width} height={height} viewBox={`0 0 ${width} ${height}`} preserveAspectRatio="none">
      <path d={line(pts)} fill="none" stroke={color} strokeWidth={1.6} vectorEffect="non-scaling-stroke" />
    </svg>
  );
}
