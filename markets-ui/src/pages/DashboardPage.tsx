import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { alerts, held, indices, news, universe, walk } from '../lib/mockData';
import { areaPath, coords, line, money } from '../lib/chart';
import Sparkline from '../components/Sparkline';
import MockDataNote from '../components/MockDataNote';

const RANGES = ['1D', '1W', '1M', '3M', '1Y', 'ALL'] as const;
const SLICE_MAP: Record<(typeof RANGES)[number], number> = { '1D': 24, '1W': 40, '1M': 70, '3M': 120, '1Y': 200, ALL: 240 };
const PALETTE = ['var(--accent)', '#56cf9b', '#6aa6f0', '#c98bd6', '#f0876a', '#e6c15f', '#7ed0c4'];

export default function DashboardPage() {
  const navigate = useNavigate();
  const [range, setRange] = useState<(typeof RANGES)[number]>('1M');

  const decorated = useMemo(
    () =>
      held.map((s) => {
        const up = s.pct >= 0;
        return { ...s, up, value: s.price * (s.shares || 0), col: up ? 'var(--up)' : 'var(--down)' };
      }),
    [],
  );
  const portValue = decorated.reduce((a, b) => a + b.value, 0);
  const portSeries = useMemo(() => walk(99, 240, 0.07, 1).slice(-SLICE_MAP[range]), [range]);
  const pPts = coords(portSeries, 1000, 240, 6);
  const portChange = decorated.reduce((a, b) => a + (b.price * b.pct) / 100 * b.shares, 0);
  const portPct = portValue - portChange ? (portChange / (portValue - portChange)) * 100 : 0;
  const portUp = portChange >= 0;

  const movers = useMemo(() => {
    const decoAll = universe.map((s) => ({ ...s, up: s.pct >= 0 }));
    const sorted = [...decoAll].sort((a, b) => b.pct - a.pct);
    return [sorted[0], sorted[1], sorted[sorted.length - 2], sorted[sorted.length - 1]];
  }, []);

  const allocation = useMemo(() => {
    const byVal = [...decorated].sort((a, b) => b.value - a.value);
    const R = 54;
    const C = 2 * Math.PI * R;
    let acc = 0;
    return byVal.map((h, i) => {
      const frac = h.value / portValue;
      const len = frac * C;
      const seg = { t: h.t, color: PALETTE[i % PALETTE.length], pctTxt: `${(frac * 100).toFixed(1)}%`, dash: `${len.toFixed(2)} ${(C - len).toFixed(2)}`, offset: -acc };
      acc += len;
      return seg;
    });
  }, [decorated, portValue]);

  return (
    <section>
      <div className="card" style={{ position: 'relative', overflow: 'hidden' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 24, alignItems: 'flex-start', justifyContent: 'space-between' }}>
          <div style={{ minWidth: 240 }}>
            <div style={{ fontSize: 12, letterSpacing: '.16em', textTransform: 'uppercase', color: 'var(--faint)', marginBottom: 10 }}>
              Total Portfolio Value <MockDataNote />
            </div>
            <div className="font-serif" style={{ fontSize: 'clamp(46px,6.6vw,82px)', lineHeight: 0.92 }}>
              {money(portValue, 2)}
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 14, marginTop: 16, flexWrap: 'wrap' }}>
              <div
                className="font-mono"
                style={{ display: 'flex', alignItems: 'center', gap: 7, padding: '7px 13px', borderRadius: 10, background: portUp ? 'var(--up-soft)' : 'var(--down-soft)', color: portUp ? 'var(--up)' : 'var(--down)', fontSize: 14, fontWeight: 500 }}
              >
                <span>{portUp ? '▲' : '▼'}</span>
                <span>{money(Math.abs(portChange), 2)}</span>
                <span style={{ opacity: 0.7 }}>({portPct.toFixed(2)}%)</span>
              </div>
              <span style={{ fontSize: 13, color: 'var(--muted)' }}>Today</span>
            </div>
          </div>
          <div className="pill-tabs">
            {RANGES.map((r) => (
              <button key={r} className={`pill-tab${range === r ? ' active' : ''}`} onClick={() => setRange(r)}>
                {r}
              </button>
            ))}
          </div>
        </div>
        <div style={{ marginTop: 18 }}>
          <svg viewBox="0 0 1000 240" preserveAspectRatio="none" style={{ width: '100%', height: 'clamp(170px,22vw,240px)', display: 'block' }}>
            <defs>
              <linearGradient id="portGradient" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stopColor="var(--accent)" stopOpacity={0.34} />
                <stop offset="100%" stopColor="var(--accent)" stopOpacity={0} />
              </linearGradient>
            </defs>
            <line x1="0" y1="60" x2="1000" y2="60" stroke="var(--line)" strokeWidth={1} />
            <line x1="0" y1="120" x2="1000" y2="120" stroke="var(--line)" strokeWidth={1} />
            <line x1="0" y1="180" x2="1000" y2="180" stroke="var(--line)" strokeWidth={1} />
            <path d={areaPath(pPts, 240)} fill="url(#portGradient)" stroke="none" />
            <path d={line(pPts)} fill="none" stroke="var(--accent)" strokeWidth={2.4} vectorEffect="non-scaling-stroke" strokeLinecap="round" />
          </svg>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(190px,1fr))', gap: 14, marginTop: 18 }}>
        {indices.map((ix) => {
          const up = ix.pct >= 0;
          const ser = walk(ix.seed, 60, up ? 0.04 : -0.04, 1);
          return (
            <div key={ix.t} className="card" style={{ padding: '15px 17px', display: 'flex', alignItems: 'center', gap: 14 }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 11.5, letterSpacing: '.1em', textTransform: 'uppercase', color: 'var(--faint)' }}>{ix.t}</div>
                <div className="font-mono" style={{ fontSize: 18, marginTop: 5 }}>
                  {ix.v.toLocaleString('en-IN', { maximumFractionDigits: 2 })}
                </div>
                <div className="font-mono" style={{ fontSize: 12, color: up ? 'var(--up)' : 'var(--down)', marginTop: 3 }}>
                  {up ? '▲' : '▼'} {up ? '+' : ''}
                  {ix.pct.toFixed(2)}%
                </div>
              </div>
              <Sparkline series={ser} width={70} height={40} color={up ? 'var(--up)' : 'var(--down)'} />
            </div>
          );
        })}
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, marginTop: 18, alignItems: 'flex-start' }}>
        <div className="card" style={{ flex: 2.2, minWidth: 440, padding: 0, overflow: 'hidden' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '20px 22px 14px' }}>
            <h2 className="card-title" style={{ margin: 0, fontSize: 24 }}>
              Watchlist <MockDataNote />
            </h2>
            <span style={{ fontSize: 12, color: 'var(--faint)' }}>{decorated.length} tracked</span>
          </div>
          <div className="grid-table-head" style={{ gridTemplateColumns: '1.7fr 1fr 1.1fr 1.1fr 76px' }}>
            <span>Symbol</span>
            <span className="text-right">Price</span>
            <span className="text-right">24h</span>
            <span className="text-right">Holdings</span>
            <span className="text-right">7d</span>
          </div>
          {decorated.map((row) => (
            <div
              key={row.t}
              className="grid-table-row"
              style={{ gridTemplateColumns: '1.7fr 1fr 1.1fr 1.1fr 76px' }}
              onClick={() => navigate(`/stock/${row.t}`)}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
                <div className="ticker-badge" style={{ width: 36, height: 36, fontSize: 11 }}>
                  {row.t}
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 500 }}>{row.t}</div>
                  <div style={{ fontSize: 12, color: 'var(--muted)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{row.n}</div>
                </div>
              </div>
              <div className="font-mono text-right" style={{ fontSize: 13.5 }}>
                {money(row.price)}
              </div>
              <div className="font-mono text-right" style={{ fontSize: 13, color: row.col }}>
                {row.up ? '+' : ''}
                {row.pct.toFixed(2)}%
              </div>
              <div className="font-mono text-right" style={{ fontSize: 13, color: 'var(--muted)' }}>
                {row.shares ? money(row.value, 0) : '—'}
              </div>
              <Sparkline series={row.series.slice(-42)} width={76} height={30} color={row.col} />
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minWidth: 300, display: 'flex', flexDirection: 'column', gap: 18 }}>
          <div className="card">
            <h2 className="card-title">
              Allocation <MockDataNote />
            </h2>
            <div style={{ display: 'flex', alignItems: 'center', gap: 20 }}>
              <div style={{ position: 'relative', flex: 'none' }}>
                <svg width="128" height="128" viewBox="0 0 128 128" style={{ transform: 'rotate(-90deg)' }}>
                  <circle cx="64" cy="64" r="54" fill="none" stroke="var(--surface-2)" strokeWidth={14} />
                  {allocation.map((seg) => (
                    <circle key={seg.t} cx="64" cy="64" r="54" fill="none" stroke={seg.color} strokeWidth={14} strokeDasharray={seg.dash} strokeDashoffset={seg.offset} />
                  ))}
                </svg>
                <div style={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center' }}>
                  <span style={{ fontSize: 10.5, color: 'var(--faint)' }}>HOLDINGS</span>
                  <span className="font-mono" style={{ fontSize: 20 }}>
                    {decorated.length}
                  </span>
                </div>
              </div>
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 9, minWidth: 0 }}>
                {allocation.map((seg) => (
                  <div key={seg.t} style={{ display: 'flex', alignItems: 'center', gap: 9 }}>
                    <span style={{ width: 9, height: 9, borderRadius: 3, background: seg.color, flex: 'none' }} />
                    <span style={{ fontSize: 12.5, flex: 1 }}>{seg.t}</span>
                    <span className="font-mono" style={{ fontSize: 12, color: 'var(--muted)' }}>
                      {seg.pctTxt}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          </div>

          <div className="card">
            <h2 className="card-title">Top Movers</h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
              {movers.map((m) => (
                <div key={m.t} style={{ display: 'flex', alignItems: 'center', gap: 12, cursor: 'pointer' }} onClick={() => navigate(`/stock/${m.t}`)}>
                  <div className="ticker-badge" style={{ width: 34, height: 34, fontSize: 10.5, background: m.up ? 'var(--up-soft)' : 'var(--down-soft)', color: m.up ? 'var(--up)' : 'var(--down)' }}>
                    {m.t}
                  </div>
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{ fontSize: 13, fontWeight: 500 }}>{m.t}</div>
                    <div style={{ fontSize: 11.5, color: 'var(--muted)' }}>{m.n}</div>
                  </div>
                  <div style={{ textAlign: 'right', flex: 'none' }}>
                    <div className="font-mono" style={{ fontSize: 12.5 }}>
                      {money(m.price)}
                    </div>
                    <div className="font-mono" style={{ fontSize: 11.5, color: m.up ? 'var(--up)' : 'var(--down)' }}>
                      {m.up ? '▲' : '▼'} {m.pct.toFixed(2)}%
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, marginTop: 18, alignItems: 'flex-start' }}>
        <div className="card" style={{ flex: 2.2, minWidth: 440 }}>
          <h2 className="card-title">
            Market Wire <MockDataNote />
          </h2>
          {news.map((a, i) => (
            <div key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 14, padding: '14px 0', borderTop: '1px solid var(--line)' }}>
              <span className="font-mono" style={{ fontSize: 11, color: 'var(--faint)', width: 34, flex: 'none' }}>
                {a.time}
              </span>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 14, lineHeight: 1.4 }}>{a.h}</div>
                <div style={{ fontSize: 12, color: 'var(--muted)', marginTop: 5 }}>{a.s}</div>
              </div>
              {a.tag && (
                <span className="font-mono" style={{ fontSize: 11, color: 'var(--accent)', background: 'var(--accent-soft)', padding: '4px 8px', borderRadius: 7, flex: 'none' }}>
                  {a.tag}
                </span>
              )}
            </div>
          ))}
        </div>

        <div className="card" style={{ flex: 1, minWidth: 300 }}>
          <h2 className="card-title">
            Price Alerts <MockDataNote />
          </h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 11 }}>
            {alerts.map((al) => (
              <div key={al.t} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '11px 13px', background: 'var(--surface-2)', borderRadius: 13 }}>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <div style={{ fontSize: 13 }}>
                    <span style={{ fontWeight: 600 }}>{al.t}</span> <span style={{ color: 'var(--muted)' }}>{al.dir === 'above' ? 'rises above' : 'falls below'}</span>
                  </div>
                  <div className="font-mono" style={{ fontSize: 12, color: 'var(--muted)', marginTop: 2 }}>
                    {money(al.target, 2)}
                  </div>
                </div>
                <span style={{ fontSize: 11, color: al.active ? 'var(--up)' : 'var(--faint)', fontWeight: 500 }}>{al.active ? 'Armed' : 'Paused'}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
