import { useEffect, useRef, useState } from 'react';
import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { searchStocks } from '../api/client';
import type { EquitySearchResult } from '../types';
import { useTheme } from '../ThemeContext';

function SunIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
      <circle cx="12" cy="12" r="4.2" />
      <path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M19.1 4.9l-1.8 1.8M6.7 17.3l-1.8 1.8" />
    </svg>
  );
}

function MoonIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
    </svg>
  );
}

function BellIcon() {
  return (
    <svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth={2}>
      <path d="M18 8A6 6 0 1 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.7 21a2 2 0 0 1-3.4 0" />
    </svg>
  );
}

const NAV_ITEMS = [
  { to: '/', label: 'Dashboard', end: true },
  { to: '/watchlist', label: 'Watchlist' },
  { to: '/markets', label: 'Markets' },
  { to: '/portfolio', label: 'Portfolio' },
];

const SIGNAL_ITEMS = [
  { to: '/news', label: 'Market Wire' },
  { to: '/alerts', label: 'Alerts' },
];

export default function Layout() {
  const { theme, toggleTheme } = useTheme();
  const navigate = useNavigate();
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<EquitySearchResult[]>([]);
  const [searchOpen, setSearchOpen] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    if (debounceRef.current) {
      clearTimeout(debounceRef.current);
    }
    if (!query.trim()) {
      setResults([]);
      return;
    }
    debounceRef.current = setTimeout(async () => {
      try {
        const r = await searchStocks(query, 8);
        setResults(r);
      } catch {
        setResults([]);
      }
    }, 250);
    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current);
    };
  }, [query]);

  function openStock(symbol: string) {
    setSearchOpen(false);
    setQuery('');
    navigate(`/stock/${symbol}`);
  }

  return (
    <div style={{ position: 'relative', minHeight: '100vh' }}>
      <div className="app-bg-glow" />

      <header className="topbar">
        <div className="topbar-inner">
          <NavLink to="/" className="logo">
            <svg width="26" height="26" viewBox="0 0 26 26">
              <g fill="var(--accent)">
                <rect x="3" y="15.4" width="20" height="3.4" rx="1.7" />
                <rect x="6" y="9.7" width="14" height="3.4" rx="1.7" opacity=".62" />
                <rect x="9" y="4" width="8" height="3.4" rx="1.7" opacity=".38" />
              </g>
            </svg>
            <span className="logo-title">Strata</span>
          </NavLink>

          <div className="search-wrap">
            <div className="search-box">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="var(--faint)" strokeWidth={2}>
                <circle cx="11" cy="11" r="7" />
                <path d="m20 20-3.5-3.5" />
              </svg>
              <input
                className="search-input"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value);
                  setSearchOpen(true);
                }}
                onFocus={() => setSearchOpen(true)}
                placeholder="Search stocks by symbol or company…"
              />
              <span className="search-kbd">⌘K</span>
            </div>

            {searchOpen && query.trim() && (
              <>
                <div className="search-dropdown">
                  <div className="search-dropdown-label">
                    {results.length} result{results.length === 1 ? '' : 's'}
                  </div>
                  {results.map((r) => (
                    <div key={r.symbol} className="search-result-row" onClick={() => openStock(r.symbol)}>
                      <div className="ticker-badge" style={{ width: 38, height: 38, fontSize: 12 }}>
                        {r.symbol.slice(0, 4)}
                      </div>
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ fontSize: 13.5, fontWeight: 500 }}>{r.symbol}</div>
                        <div
                          style={{
                            fontSize: 12,
                            color: 'var(--muted)',
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis',
                          }}
                        >
                          {r.companyName ?? r.sector ?? 'NSE equity'}
                        </div>
                      </div>
                    </div>
                  ))}
                  {results.length === 0 && (
                    <div style={{ padding: '14px 16px', fontSize: 13, color: 'var(--muted)' }}>
                      No matches for "{query}"
                    </div>
                  )}
                </div>
                <div className="search-backdrop" onClick={() => setSearchOpen(false)} />
              </>
            )}
          </div>

          <nav style={{ display: 'flex', alignItems: 'center', gap: 6, marginLeft: 'auto', flex: 'none' }}>
            <div className="market-status">
              <span className="market-status-dot" />
              <span style={{ fontSize: 12, color: 'var(--up)', fontWeight: 500 }}>Markets Open</span>
            </div>
            <button className="icon-btn" onClick={toggleTheme} title="Toggle theme">
              {theme === 'dark' ? <MoonIcon /> : <SunIcon />}
            </button>
            <button className="icon-btn" title="Alerts">
              <BellIcon />
              <span
                style={{
                  position: 'absolute',
                  top: 8,
                  right: 9,
                  width: 7,
                  height: 7,
                  borderRadius: '50%',
                  background: 'var(--accent)',
                }}
              />
            </button>
            <div className="avatar">A</div>
          </nav>
        </div>
      </header>

      <div className="shell">
        <aside className="sidebar">
          <div className="sidebar-group">
            <div className="sidebar-label">Overview</div>
            {NAV_ITEMS.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}
              >
                {item.label}
              </NavLink>
            ))}
          </div>
          <div className="sidebar-group">
            <div className="sidebar-label">Signals</div>
            {SIGNAL_ITEMS.map((item) => (
              <NavLink key={item.to} to={item.to} className={({ isActive }) => `nav-item${isActive ? ' active' : ''}`}>
                {item.label}
              </NavLink>
            ))}
          </div>
          <div className="sidebar-card">
            <div style={{ fontSize: 11, letterSpacing: '.12em', textTransform: 'uppercase', color: 'var(--faint)' }}>
              Data Source
            </div>
            <div style={{ fontFamily: "'JetBrains Mono',monospace", fontSize: 13, marginTop: 7, color: 'var(--muted)' }}>
              NSE Bhavcopy · stock-discovery
            </div>
          </div>
        </aside>

        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
