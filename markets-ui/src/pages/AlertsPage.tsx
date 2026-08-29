import { useNavigate } from 'react-router-dom';
import { alerts, universe } from '../lib/mockData';
import MockDataNote from '../components/MockDataNote';

export default function AlertsPage() {
  const navigate = useNavigate();
  const active = alerts.filter((a) => a.active).length;

  return (
    <section>
      <h1 className="section-title">
        Alerts <MockDataNote />
      </h1>
      <p style={{ margin: '8px 0 22px', fontSize: 13.5, color: 'var(--muted)' }}>
        {active} active price {active === 1 ? 'alert' : 'alerts'} out of {alerts.length}
      </p>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        <div className="grid-table-head" style={{ gridTemplateColumns: '1.8fr 1fr 1fr 1fr', padding: '16px 22px 10px' }}>
          <span>Symbol</span>
          <span className="text-right">Condition</span>
          <span className="text-right">Target</span>
          <span className="text-right">Status</span>
        </div>
        {alerts.map((a, i) => {
          const stock = universe.find((s) => s.t === a.t);
          return (
            <div key={i} className="grid-table-row" style={{ gridTemplateColumns: '1.8fr 1fr 1fr 1fr' }} onClick={() => navigate(`/stock/${a.t}`)}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
                <div className="ticker-badge" style={{ width: 36, height: 36, fontSize: 11 }}>
                  {a.t}
                </div>
                <div style={{ minWidth: 0 }}>
                  <div style={{ fontSize: 14, fontWeight: 500 }}>{a.t}</div>
                  {stock && <div style={{ fontSize: 12, color: 'var(--muted)' }}>{stock.n}</div>}
                </div>
              </div>
              <div className="font-mono text-right" style={{ fontSize: 13, color: 'var(--muted)', textTransform: 'capitalize' }}>
                Price {a.dir}
              </div>
              <div className="font-mono text-right" style={{ fontSize: 13.5 }}>
                ${a.target.toLocaleString()}
              </div>
              <div className="text-right">
                <span
                  style={{
                    fontSize: 11.5,
                    padding: '3px 10px',
                    borderRadius: 20,
                    background: a.active ? 'var(--up-soft)' : 'var(--surface-2)',
                    color: a.active ? 'var(--up)' : 'var(--faint)',
                  }}
                >
                  {a.active ? 'Active' : 'Paused'}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}
