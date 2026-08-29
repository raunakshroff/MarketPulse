import { useNavigate } from 'react-router-dom';
import { news, universe } from '../lib/mockData';
import MockDataNote from '../components/MockDataNote';

export default function NewsPage() {
  const navigate = useNavigate();
  const tickerName = (t: string | null) => (t ? universe.find((s) => s.t === t)?.n : null);

  return (
    <section>
      <h1 className="section-title">
        Market Wire <MockDataNote />
      </h1>
      <p style={{ margin: '8px 0 22px', fontSize: 13.5, color: 'var(--muted)' }}>Headlines across your holdings and the broader market.</p>

      <div className="card" style={{ padding: 0, overflow: 'hidden' }}>
        {news.map((item, i) => (
          <div
            key={i}
            style={{
              display: 'flex',
              gap: 16,
              alignItems: 'flex-start',
              padding: '18px 22px',
              borderBottom: i === news.length - 1 ? 'none' : '1px solid var(--border)',
              cursor: item.tag ? 'pointer' : 'default',
            }}
            onClick={() => item.tag && navigate(`/stock/${item.tag}`)}
          >
            <div style={{ width: 6, height: 6, borderRadius: '50%', background: 'var(--accent)', marginTop: 7, flexShrink: 0 }} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 14.5, lineHeight: 1.45 }}>{item.h}</div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginTop: 8, fontSize: 12, color: 'var(--faint)' }}>
                <span>{item.s}</span>
                <span>·</span>
                <span>{item.time} ago</span>
                {item.tag && (
                  <span className="ticker-badge" style={{ padding: '2px 8px', fontSize: 10.5, height: 'auto', width: 'auto', borderRadius: 5 }}>
                    {item.tag}
                  </span>
                )}
                {item.tag && tickerName(item.tag) && <span style={{ color: 'var(--muted)' }}>{tickerName(item.tag)}</span>}
              </div>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
