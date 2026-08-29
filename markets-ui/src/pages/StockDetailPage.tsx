import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getFundamentals, getHistory } from '../api/client';
import type { EquityRecord, FundamentalsView } from '../types';
import { areaPath, candles, coords, line, money } from '../lib/chart';

type ChartType = 'area' | 'candle';
type RangeKey = '1M' | '3M' | '6M' | 'ALL';
const RANGES: RangeKey[] = ['1M', '3M', '6M', 'ALL'];
const RANGE_DAYS: Record<RangeKey, number> = { '1M': 22, '3M': 66, '6M': 132, ALL: 10000 };

function BackIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
      <path d="m15 18-6-6 6-6" />
    </svg>
  );
}

export default function StockDetailPage() {
  const { symbol } = useParams<{ symbol: string }>();
  const [history, setHistory] = useState<EquityRecord[] | null>(null);
  const [fundamentals, setFundamentals] = useState<FundamentalsView | null | undefined>(undefined);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [chartType, setChartType] = useState<ChartType>('area');
  const [range, setRange] = useState<RangeKey>('ALL');

  useEffect(() => {
    if (!symbol) return;
    setHistory(null);
    setFundamentals(undefined);
    setLoadError(null);
    getHistory(symbol)
      .then(setHistory)
      .catch(() => setLoadError('Could not load price history for this symbol.'));
    getFundamentals(symbol).then(setFundamentals);
  }, [symbol]);

  const visibleHistory = useMemo(() => {
    if (!history) return [];
    const days = RANGE_DAYS[range];
    return history.slice(-days);
  }, [history, range]);

  const closes = useMemo(() => visibleHistory.map((r) => r.closePrice ?? 0), [visibleHistory]);
  const pts = useMemo(() => coords(closes, 1000, 340, 8), [closes]);
  const latest = history && history.length > 0 ? history[history.length - 1] : null;
  const previous = history && history.length > 1 ? history[history.length - 2] : null;
  const change =
    latest?.closePrice != null && previous?.closePrice != null ? latest.closePrice - previous.closePrice : null;
  const pctChange = change != null && previous?.closePrice ? (change / previous.closePrice) * 100 : null;
  const up = (pctChange ?? 0) >= 0;

  if (loadError) {
    return (
      <section>
        <Link to="/" className="back-link">
          <BackIcon /> Back to dashboard
        </Link>
        <div className="card">{loadError}</div>
      </section>
    );
  }

  if (history === null) {
    return (
      <section>
        <Link to="/" className="back-link">
          <BackIcon /> Back to dashboard
        </Link>
        <div className="card">Loading {symbol}…</div>
      </section>
    );
  }

  if (history.length === 0) {
    return (
      <section>
        <Link to="/" className="back-link">
          <BackIcon /> Back to dashboard
        </Link>
        <div className="card">No price history found for "{symbol}".</div>
      </section>
    );
  }

  const stats = latest
    ? [
        { k: 'Open', v: latest.openPrice != null ? money(latest.openPrice) : '—' },
        { k: 'Prev Close', v: latest.prevClose != null ? money(latest.prevClose) : '—' },
        { k: 'Day High', v: latest.highPrice != null ? money(latest.highPrice) : '—' },
        { k: 'Day Low', v: latest.lowPrice != null ? money(latest.lowPrice) : '—' },
        { k: 'Volume', v: latest.ttlTradedQty.toLocaleString('en-IN') },
        { k: 'Trades', v: latest.noOfTrades.toLocaleString('en-IN') },
        { k: 'Delivery Qty', v: latest.delivQty.toLocaleString('en-IN') },
        { k: 'Delivery %', v: latest.delivPer != null ? `${latest.delivPer.toFixed(2)}%` : '—' },
        ...(fundamentals?.marketCap != null
          ? [{ k: 'Market Cap', v: money(fundamentals.marketCap, 0) }]
          : []),
        ...(fundamentals?.trailingPe != null
          ? [{ k: 'P/E Ratio', v: fundamentals.trailingPe.toFixed(1) }]
          : []),
        ...(fundamentals?.fiftyTwoWeekHigh != null
          ? [{ k: '52W High', v: money(fundamentals.fiftyTwoWeekHigh) }]
          : []),
        ...(fundamentals?.fiftyTwoWeekLow != null
          ? [{ k: '52W Low', v: money(fundamentals.fiftyTwoWeekLow) }]
          : []),
      ]
    : [];

  const chartColor = up ? 'var(--up)' : 'var(--down)';

  return (
    <section>
      <Link to="/" className="back-link">
        <BackIcon /> Back to dashboard
      </Link>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, alignItems: 'flex-start' }}>
        {/* chart card */}
        <div className="card" style={{ flex: 2.4, minWidth: 460 }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, alignItems: 'flex-start', justifyContent: 'space-between' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 16 }}>
              <div className="ticker-badge" style={{ width: 54, height: 54, fontSize: 15 }}>
                {symbol?.slice(0, 4)}
              </div>
              <div>
                <div style={{ display: 'flex', alignItems: 'baseline', gap: 10 }}>
                  <h1 className="font-serif" style={{ margin: 0, fontSize: 32, fontWeight: 400, lineHeight: 1 }}>
                    {symbol}
                  </h1>
                  {fundamentals?.sector && <span style={{ fontSize: 13, color: 'var(--faint)' }}>{fundamentals.sector}</span>}
                </div>
                <div style={{ fontSize: 14, color: 'var(--muted)', marginTop: 5 }}>
                  {fundamentals?.companyName ?? 'NSE equity'}
                </div>
              </div>
            </div>
            <div style={{ textAlign: 'right' }}>
              <div className="font-serif" style={{ fontSize: 42, lineHeight: 0.95 }}>
                {latest?.closePrice != null ? money(latest.closePrice) : '—'}
              </div>
              {change != null && pctChange != null && (
                <div
                  className="font-mono"
                  style={{
                    display: 'inline-flex',
                    alignItems: 'center',
                    gap: 7,
                    marginTop: 8,
                    padding: '5px 11px',
                    borderRadius: 9,
                    background: up ? 'var(--up-soft)' : 'var(--down-soft)',
                    color: chartColor,
                    fontSize: 13,
                  }}
                >
                  {up ? '▲' : '▼'} {up ? '+' : '−'}
                  {money(Math.abs(change))} ({up ? '+' : ''}
                  {pctChange.toFixed(2)}%)
                </div>
              )}
            </div>
          </div>

          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, justifyContent: 'space-between', alignItems: 'center', margin: '22px 0 8px' }}>
            <div className="pill-tabs">
              {RANGES.map((r) => (
                <button key={r} className={`pill-tab${range === r ? ' active' : ''}`} onClick={() => setRange(r)}>
                  {r}
                </button>
              ))}
            </div>
            <div className="pill-tabs">
              <button className={`pill-tab${chartType === 'area' ? ' active' : ''}`} onClick={() => setChartType('area')}>
                Line
              </button>
              <button className={`pill-tab${chartType === 'candle' ? ' active' : ''}`} onClick={() => setChartType('candle')}>
                Candles
              </button>
            </div>
          </div>

          <svg viewBox="0 0 1000 340" preserveAspectRatio="none" style={{ width: '100%', height: 'clamp(220px,26vw,340px)', display: 'block', overflow: 'visible' }}>
            <defs>
              <linearGradient id="detailGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor={chartColor} stopOpacity={0.26} />
                <stop offset="100%" stopColor={chartColor} stopOpacity={0} />
              </linearGradient>
            </defs>
            <line x1="0" y1="85" x2="1000" y2="85" stroke="var(--line)" strokeWidth={1} />
            <line x1="0" y1="170" x2="1000" y2="170" stroke="var(--line)" strokeWidth={1} />
            <line x1="0" y1="255" x2="1000" y2="255" stroke="var(--line)" strokeWidth={1} />
            {chartType === 'area' && pts.length > 0 && (
              <>
                <path d={areaPath(pts, 340)} fill="url(#detailGradient)" stroke="none" />
                <path d={line(pts)} fill="none" stroke={chartColor} strokeWidth={2.4} vectorEffect="non-scaling-stroke" strokeLinecap="round" />
              </>
            )}
            {chartType === 'candle' &&
              candles(
                visibleHistory.map((r) => ({
                  open: r.openPrice ?? 0,
                  high: r.highPrice ?? 0,
                  low: r.lowPrice ?? 0,
                  close: r.closePrice ?? 0,
                })),
                1000,
                340,
              ).map((c, i) => (
                <g key={i}>
                  <line x1={c.cx} x2={c.cx} y1={c.yH} y2={c.yL} stroke={c.up ? 'var(--up)' : 'var(--down)'} strokeWidth={1.1} vectorEffect="non-scaling-stroke" />
                  <rect x={c.x} y={c.bodyY} width={c.bodyW} height={c.bodyH} fill={c.up ? 'var(--up)' : 'var(--down)'} rx={0.8} />
                </g>
              ))}
          </svg>
        </div>

        {/* right column */}
        <div style={{ flex: 1, minWidth: 300, display: 'flex', flexDirection: 'column', gap: 18 }}>
          <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
            <button className="btn-primary">+ Add to Watchlist</button>
            <button className="btn-secondary">Set Price Alert</button>
          </div>

          <div className="card">
            <h2 className="card-title" style={{ fontSize: 21 }}>
              Key Statistics
            </h2>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '14px 18px' }}>
              {stats.map((st) => (
                <div key={st.k}>
                  <div style={{ fontSize: 11.5, color: 'var(--faint)' }}>{st.k}</div>
                  <div className="font-mono" style={{ fontSize: 14, marginTop: 4 }}>
                    {st.v}
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="card">
            <h2 className="card-title" style={{ fontSize: 21 }}>
              About
            </h2>
            {fundamentals === undefined ? (
              <p style={{ margin: 0, fontSize: 13.5, color: 'var(--muted)' }}>Loading…</p>
            ) : fundamentals === null ? (
              <p style={{ margin: 0, fontSize: 13.5, color: 'var(--muted)' }}>
                Fundamentals haven't been fetched for this symbol yet.
                <span className="pending-note">not yet refreshed</span>
              </p>
            ) : (
              <p style={{ margin: 0, fontSize: 13.5, lineHeight: 1.6, color: 'var(--muted)' }}>
                {fundamentals.description ?? 'No description available.'}
              </p>
            )}
          </div>
        </div>
      </div>
    </section>
  );
}
