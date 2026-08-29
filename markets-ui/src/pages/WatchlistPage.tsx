import { useNavigate } from 'react-router-dom';
import { held } from '../lib/mockData';
import { money } from '../lib/chart';
import Sparkline from '../components/Sparkline';
import MockDataNote from '../components/MockDataNote';

export default function WatchlistPage() {
  const navigate = useNavigate();
  const decorated = held.map((s) => ({ ...s, up: s.pct >= 0, value: s.price * (s.shares || 0) }));
  const gainers = decorated.filter((h) => h.up).length;
  const best = [...decorated].sort((a, b) => b.pct - a.pct)[0];
  const worst = [...decorated].sort((a, b) => a.pct - b.pct)[0];

  return (
    <section>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 16, alignItems: 'flex-end', justifyContent: 'space-between', marginBottom: 20 }}>
        <div>
          <h1 className="section-title">
            Watchlist <MockDataNote />
          </h1>
          <p style={{ margin: '8px 0 0', fontSize: 13.5, color: 'var(--muted)' }}>{decorated.length} instruments tracked</p>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 14, marginBottom: 18 }}>
        <div className="stat-tile">
          <div style={{ fontSize: 11.5, textTransform: 'uppercase', color: 'var(--faint)' }}>Tracked</div>
          <div className="font-mono" style={{ fontSize: 19, marginTop: 7 }}>
            {decorated.length}
          </div>
        </div>
        <div className="stat-tile">
          <div style={{ fontSize: 11.5, textTransform: 'uppercase', color: 'var(--faint)' }}>Advancing</div>
          <div className="font-mono up" style={{ fontSize: 19, marginTop: 7 }}>
            {gainers} / {decorated.length}
          </div>
        </div>
        <div className="stat-tile">
          <div style={{ fontSize: 11.5, textTransform: 'uppercase', color: 'var(--faint)' }}>Best Today</div>
          <div className="font-mono up" style={{ fontSize: 19, marginTop: 7 }}>
            {best.t} +{best.pct.toFixed(2)}%
          </div>
        </div>
        <div className="stat-tile">
          <div style={{ fontSize: 11.5, textTransform: 'uppercase', color: 'var(--faint)' }}>Worst Today</div>
          <div className="font-mono down" style={{ fontSize: 19, marginTop: 7 }}>
            {worst.t} {worst.pct.toFixed(2)}%
          </div>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div className="grid-table-head" style={{ gridTemplateColumns: '1.8fr .9fr .9fr .9fr .9fr 82px', padding: '16px 22px 10px' }}>
          <span>Symbol</span>
          <span className="text-right">Price</span>
          <span className="text-right">24h</span>
          <span className="text-right">Shares</span>
          <span className="text-right">Value</span>
          <span className="text-right">7d</span>
        </div>
        {decorated.map((row) => (
          <div key={row.t} className="grid-table-row" style={{ gridTemplateColumns: '1.8fr .9fr .9fr .9fr .9fr 82px' }} onClick={() => navigate(`/stock/${row.t}`)}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
              <div className="ticker-badge" style={{ width: 36, height: 36, fontSize: 11 }}>
                {row.t}
              </div>
              <div style={{ minWidth: 0 }}>
                <div style={{ fontSize: 14, fontWeight: 500 }}>{row.t}</div>
                <div style={{ fontSize: 12, color: 'var(--muted)' }}>{row.n}</div>
              </div>
            </div>
            <div className="font-mono text-right" style={{ fontSize: 13.5 }}>
              {money(row.price)}
            </div>
            <div className={`font-mono text-right ${row.up ? 'up' : 'down'}`} style={{ fontSize: 13 }}>
              {row.up ? '+' : ''}
              {row.pct.toFixed(2)}%
            </div>
            <div className="font-mono text-right" style={{ fontSize: 13, color: 'var(--muted)' }}>
              {row.shares || '—'}
            </div>
            <div className="font-mono text-right" style={{ fontSize: 13 }}>
              {money(row.value, 0)}
            </div>
            <Sparkline series={row.series.slice(-42)} width={82} height={30} color={row.up ? 'var(--up)' : 'var(--down)'} />
          </div>
        ))}
      </div>
    </section>
  );
}
