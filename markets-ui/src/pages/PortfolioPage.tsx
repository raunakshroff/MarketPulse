import { useNavigate } from 'react-router-dom';
import { held } from '../lib/mockData';
import { money } from '../lib/chart';
import MockDataNote from '../components/MockDataNote';

export default function PortfolioPage() {
  const navigate = useNavigate();
  const decorated = held
    .map((s) => {
      const up = s.pct >= 0;
      const value = s.price * (s.shares || 0);
      const dayPl = ((s.price * s.pct) / 100) * (s.shares || 0);
      return { ...s, up, value, dayPl };
    })
    .sort((a, b) => b.value - a.value);
  const portValue = decorated.reduce((a, b) => a + b.value, 0);
  const costBasis = portValue * 0.82;
  const bestContributor = [...decorated].sort((a, b) => b.dayPl - a.dayPl)[0];

  return (
    <section>
      <h1 className="section-title">
        Portfolio <MockDataNote />
      </h1>
      <p style={{ margin: '8px 0 22px', fontSize: 13.5, color: 'var(--muted)' }}>Holdings, weights and contribution to today's move.</p>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 14, marginBottom: 18 }}>
        <div className="stat-tile">
          <div style={{ fontSize: 11.5, textTransform: 'uppercase', color: 'var(--faint)' }}>Cost Basis</div>
          <div className="font-mono" style={{ fontSize: 19, marginTop: 7 }}>
            {money(costBasis, 0)}
          </div>
        </div>
        <div className="stat-tile">
          <div style={{ fontSize: 11.5, textTransform: 'uppercase', color: 'var(--faint)' }}>Total Return</div>
          <div className="font-mono up" style={{ fontSize: 19, marginTop: 7 }}>
            +{money(portValue - costBasis, 0)}
          </div>
        </div>
        <div className="stat-tile">
          <div style={{ fontSize: 11.5, textTransform: 'uppercase', color: 'var(--faint)' }}>Positions</div>
          <div className="font-mono" style={{ fontSize: 19, marginTop: 7 }}>
            {decorated.length}
          </div>
        </div>
        <div className="stat-tile">
          <div style={{ fontSize: 11.5, textTransform: 'uppercase', color: 'var(--faint)' }}>Top Contributor</div>
          <div className="font-mono up" style={{ fontSize: 19, marginTop: 7 }}>
            {bestContributor.t}
          </div>
        </div>
      </div>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div className="grid-table-head" style={{ gridTemplateColumns: '1.8fr 1fr .9fr .9fr .9fr', padding: '16px 22px 10px' }}>
          <span>Symbol</span>
          <span className="text-right">Value</span>
          <span className="text-right">Weight</span>
          <span className="text-right">24h</span>
          <span className="text-right">Day P/L</span>
        </div>
        {decorated.map((row) => (
          <div key={row.t} className="grid-table-row" style={{ gridTemplateColumns: '1.8fr 1fr .9fr .9fr .9fr' }} onClick={() => navigate(`/stock/${row.t}`)}>
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
              {money(row.value, 0)}
            </div>
            <div className="font-mono text-right" style={{ fontSize: 13, color: 'var(--muted)' }}>
              {((row.value / portValue) * 100).toFixed(1)}%
            </div>
            <div className={`font-mono text-right ${row.up ? 'up' : 'down'}`} style={{ fontSize: 13 }}>
              {row.up ? '+' : ''}
              {row.pct.toFixed(2)}%
            </div>
            <div className={`font-mono text-right ${row.dayPl >= 0 ? 'up' : 'down'}`} style={{ fontSize: 13 }}>
              {row.dayPl >= 0 ? '+' : '−'}
              {money(Math.abs(row.dayPl), 0)}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
