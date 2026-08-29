import { useNavigate } from 'react-router-dom';
import { indices, universe, walk } from '../lib/mockData';
import Sparkline from '../components/Sparkline';
import MockDataNote from '../components/MockDataNote';

export default function MarketsPage() {
  const navigate = useNavigate();
  const decoAll = universe.map((s) => ({ ...s, up: s.pct >= 0 }));
  const sorted = [...decoAll].sort((a, b) => b.pct - a.pct);
  const movers = [sorted[0], sorted[1], sorted[sorted.length - 2], sorted[sorted.length - 1]];

  const secMap = new Map<string, number[]>();
  decoAll.forEach((s) => {
    const arr = secMap.get(s.sec) ?? [];
    arr.push(s.pct);
    secMap.set(s.sec, arr);
  });
  const sectors = [...secMap.entries()]
    .map(([t, pcts]) => ({ t, avg: pcts.reduce((a, b) => a + b, 0) / pcts.length }))
    .sort((a, b) => b.avg - a.avg);
  const maxAbs = Math.max(...sectors.map((s) => Math.abs(s.avg)), 1);

  return (
    <section>
      <h1 className="section-title">
        Markets <MockDataNote />
      </h1>
      <p style={{ margin: '8px 0 22px', fontSize: 13.5, color: 'var(--muted)' }}>Indices, sectors and the full tradable universe.</p>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(200px,1fr))', gap: 14 }}>
        {indices.map((ix) => {
          const up = ix.pct >= 0;
          const ser = walk(ix.seed, 60, up ? 0.04 : -0.04, 1);
          return (
            <div key={ix.t} className="card" style={{ padding: '18px 20px' }}>
              <div style={{ fontSize: 11.5, textTransform: 'uppercase', color: 'var(--faint)' }}>{ix.t}</div>
              <div className="font-serif" style={{ fontSize: 29, marginTop: 8, lineHeight: 1 }}>
                {ix.v.toLocaleString('en-IN')}
              </div>
              <div className={`font-mono ${up ? 'up' : 'down'}`} style={{ fontSize: 12.5, marginTop: 7 }}>
                {up ? '▲' : '▼'} {up ? '+' : ''}
                {ix.pct.toFixed(2)}%
              </div>
              <div style={{ marginTop: 10 }}>
                <Sparkline series={ser} width={200} height={52} color={up ? 'var(--up)' : 'var(--down)'} />
              </div>
            </div>
          );
        })}
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 18, marginTop: 18, alignItems: 'flex-start' }}>
        <div className="card" style={{ flex: 2, minWidth: 400, padding: 0, overflow: 'hidden' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '20px 22px 12px' }}>
            <h2 className="card-title" style={{ margin: 0, fontSize: 23 }}>
              All Instruments
            </h2>
            <span style={{ fontSize: 12, color: 'var(--faint)' }}>{decoAll.length} symbols (sample)</span>
          </div>
          <div className="grid-table-head" style={{ gridTemplateColumns: '1.8fr 1fr .9fr 1fr 82px' }}>
            <span>Symbol</span>
            <span className="text-right">Price</span>
            <span className="text-right">24h</span>
            <span className="text-right">Mkt Cap</span>
            <span className="text-right">7d</span>
          </div>
          {sorted.map((row) => (
            <div key={row.t} className="grid-table-row" style={{ gridTemplateColumns: '1.8fr 1fr .9fr 1fr 82px' }} onClick={() => navigate(`/stock/${row.t}`)}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
                <div className="ticker-badge" style={{ width: 34, height: 34, fontSize: 10.5 }}>
                  {row.t}
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 13.5, fontWeight: 500 }}>{row.t}</div>
                  <div style={{ fontSize: 11.5, color: 'var(--muted)' }}>{row.sec}</div>
                </div>
              </div>
              <div className="font-mono text-right" style={{ fontSize: 13 }}>
                ₹{row.price.toFixed(2)}
              </div>
              <div className={`font-mono text-right ${row.up ? 'up' : 'down'}`} style={{ fontSize: 12.5 }}>
                {row.up ? '+' : ''}
                {row.pct.toFixed(2)}%
              </div>
              <div className="font-mono text-right" style={{ fontSize: 12.5, color: 'var(--muted)' }}>
                ${row.mcap}
              </div>
              <Sparkline series={row.series.slice(-42)} width={82} height={28} color={row.up ? 'var(--up)' : 'var(--down)'} />
            </div>
          ))}
        </div>

        <div style={{ flex: 1, minWidth: 280, display: 'flex', flexDirection: 'column', gap: 18 }}>
          <div className="card">
            <h2 className="card-title" style={{ fontSize: 22 }}>
              Top Movers
            </h2>
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
                  <div className={`font-mono ${m.up ? 'up' : 'down'}`} style={{ fontSize: 12.5 }}>
                    {m.up ? '+' : ''}
                    {m.pct.toFixed(2)}%
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="card">
            <h2 className="card-title" style={{ fontSize: 22 }}>
              Sectors
            </h2>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {sectors.map((s) => (
                <div key={s.t}>
                  <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 6 }}>
                    <span style={{ fontSize: 12.5 }}>{s.t}</span>
                    <span className={`font-mono ${s.avg >= 0 ? 'up' : 'down'}`} style={{ fontSize: 12 }}>
                      {s.avg >= 0 ? '+' : ''}
                      {s.avg.toFixed(2)}%
                    </span>
                  </div>
                  <div style={{ height: 5, borderRadius: 4, background: 'var(--surface-2)', overflow: 'hidden' }}>
                    <div style={{ height: '100%', width: `${((Math.abs(s.avg) / maxAbs) * 100).toFixed(0)}%`, background: s.avg >= 0 ? 'var(--up)' : 'var(--down)', borderRadius: 4 }} />
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
