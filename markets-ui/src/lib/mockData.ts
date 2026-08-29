// Ported from the Strata design's buildData(). Powers the Dashboard/Watchlist/Markets/
// Portfolio/News/Alerts screens, which have no real backing service yet (portfolio holdings,
// watchlist persistence, price alerts, a news feed, and market indices are separate bounded
// contexts that don't exist in this system yet - see the plan notes). Stock Detail and search
// use real data from stock-discovery instead; this module is intentionally NOT used there.

function mulberry32(seed: number) {
  let a = seed;
  return function () {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

export function walk(seed: number, n: number, drift: number, vol: number): number[] {
  const r = mulberry32(seed);
  let v = 0;
  const a: number[] = [];
  for (let i = 0; i < n; i++) {
    v += (r() - 0.5) * 2 * vol + drift;
    a.push(v);
  }
  return a;
}

export interface MockStock {
  t: string; // ticker
  n: string; // name
  sec: string; // sector
  price: number;
  pct: number; // day change %
  shares: number; // held (portfolio)
  mcap: string;
  pe: number;
  vol: string;
  div: string;
  about: string;
  seed: number;
  series: number[];
}

function stock(
  t: string,
  n: string,
  sec: string,
  price: number,
  pct: number,
  shares: number,
  mcap: string,
  pe: number,
  vol: string,
  div: string,
  about: string,
  seed: number,
): MockStock {
  return { t, n, sec, price, pct, shares, mcap, pe, vol, div, about, seed, series: walk(seed, 240, pct >= 0 ? 0.05 : -0.05, 1) };
}

export const held: MockStock[] = [
  stock('NVDA', 'NVIDIA Corp', 'Semiconductors', 1284.32, 2.41, 120, '3.16T', 68.4, '42.1M', '0.02%', 'NVIDIA designs the accelerated-computing GPUs and AI infrastructure powering data centers, gaming, autonomous machines and robotics worldwide.', 11),
  stock('AAPL', 'Apple Inc', 'Consumer Tech', 228.51, 0.62, 300, '3.47T', 31.2, '51.8M', '0.44%', 'Apple designs and sells consumer electronics, software and services.', 3),
  stock('MSFT', 'Microsoft Corp', 'Software', 498.77, 1.12, 140, '3.71T', 38.0, '19.4M', '0.71%', 'Microsoft builds software, cloud (Azure), productivity tools and AI platforms.', 5),
  stock('TSLA', 'Tesla Inc', 'Automotive', 412.18, -1.84, 80, '1.31T', 74.6, '88.2M', '—', 'Tesla designs and manufactures electric vehicles, energy storage and solar products.', 7),
  stock('META', 'Meta Platforms', 'Social', 712.94, 3.08, 60, '1.81T', 27.3, '12.7M', '0.34%', 'Meta operates Facebook, Instagram, WhatsApp and Threads.', 13),
  stock('AMZN', 'Amazon.com', 'E-Commerce', 214.06, -0.43, 160, '2.24T', 42.9, '33.6M', '—', 'Amazon spans online retail, logistics, advertising and AWS.', 9),
  stock('GOOGL', 'Alphabet Inc', 'Software', 201.55, 0.94, 110, '2.46T', 24.8, '21.3M', '0.45%', 'Alphabet is the parent of Google - search, advertising, YouTube, Android, Cloud.', 17),
];

export const universeExtra: MockStock[] = [
  stock('AMD', 'Adv. Micro Devices', 'Semiconductors', 178.42, 1.92, 0, '288B', 45.1, '38.4M', '—', 'AMD designs high-performance CPUs and GPUs for data centers, PCs and gaming.', 21),
  stock('NFLX', 'Netflix Inc', 'Streaming', 1024.7, 0.84, 0, '438B', 47.6, '3.1M', '—', 'Netflix is a global streaming entertainment service.', 23),
  stock('COIN', 'Coinbase Global', 'Fintech', 312.61, 4.21, 0, '78B', 39.0, '9.8M', '—', 'Coinbase operates a leading platform for buying, selling and storing cryptocurrencies.', 27),
  stock('PLTR', 'Palantir Tech', 'Software', 84.33, -2.13, 0, '191B', 210.0, '44.2M', '—', 'Palantir builds data-analytics and AI platforms for governments and enterprises.', 29),
  stock('SHOP', 'Shopify Inc', 'E-Commerce', 118.94, 1.36, 0, '152B', 88.5, '8.9M', '—', 'Shopify provides commerce infrastructure for merchants.', 31),
  stock('UBER', 'Uber Technologies', 'Mobility', 92.74, 0.58, 0, '194B', 33.7, '14.1M', '—', 'Uber operates global ride-hailing, delivery and freight marketplaces.', 33),
  stock('JPM', 'JPMorgan Chase', 'Financials', 268.41, 0.91, 0, '748B', 13.4, '7.2M', '2.1%', 'JPMorgan Chase is a leading global bank.', 37),
  stock('DIS', 'Walt Disney Co', 'Media', 121.33, -0.52, 0, '219B', 36.2, '9.4M', '0.8%', 'Disney spans film studios, streaming, parks and consumer products.', 41),
];

export const universe: MockStock[] = [...held, ...universeExtra];

export const indices = [
  { t: 'NIFTY 50', v: 24812.3, pct: 0.74, seed: 51 },
  { t: 'SENSEX', v: 81423.1, pct: 1.12, seed: 52 },
  { t: 'NIFTY BANK', v: 51876.4, pct: -0.21, seed: 53 },
  { t: 'INDIA VIX', v: 13.42, pct: -3.42, seed: 54 },
];

export const news = [
  { h: 'NVIDIA unveils next-gen Rubin architecture as data-center demand accelerates', s: 'Bloomberg', time: '12m', tag: 'NVDA' },
  { h: 'Fed holds rates steady, signals two cuts later this year', s: 'Reuters', time: '41m', tag: null },
  { h: "Meta's AI ad tools drive record quarterly engagement", s: 'Wall St. Journal', time: '1h', tag: 'META' },
  { h: 'Tesla deliveries miss estimates amid intensifying price competition', s: 'CNBC', time: '2h', tag: 'TSLA' },
  { h: 'Apple expands services with new health and wellness tier', s: 'The Verge', time: '3h', tag: 'AAPL' },
  { h: 'Treasury yields ease as core inflation cools to 2.4%', s: 'Financial Times', time: '4h', tag: null },
  { h: 'Microsoft Azure posts 31% growth on enterprise AI adoption', s: 'Reuters', time: '5h', tag: 'MSFT' },
];

export const alerts = [
  { t: 'NVDA', dir: 'above' as const, target: 1300, active: true },
  { t: 'TSLA', dir: 'below' as const, target: 400, active: true },
  { t: 'META', dir: 'above' as const, target: 720, active: true },
  { t: 'AAPL', dir: 'above' as const, target: 235, active: false },
];
