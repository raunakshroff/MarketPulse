export type Theme = 'dark' | 'light';
export type Accent = 'gold' | 'emerald' | 'azure' | 'violet' | 'rose' | 'teal' | 'custom';

export const ACCENTS: { id: Exclude<Accent, 'custom'>; label: string; swatch: string }[] = [
  { id: 'gold', label: 'Gold', swatch: '#d8b15f' },
  { id: 'emerald', label: 'Emerald', swatch: '#35c98a' },
  { id: 'azure', label: 'Azure', swatch: '#4f8fee' },
  { id: 'violet', label: 'Violet', swatch: '#9a7ce6' },
  { id: 'rose', label: 'Rose', swatch: '#ea7391' },
  { id: 'teal', label: 'Teal', swatch: '#3fc0b7' },
];

// Dark backgrounds want a lighter, more saturated accent; light backgrounds want it darker and
// slightly less saturated so it stays readable - same contrast trade-off as the preset palette.
export function customAccentColor(hue: number, theme: Theme): string {
  return theme === 'light' ? `hsl(${hue} 62% 40%)` : `hsl(${hue} 70% 66%)`;
}
export function customAccentSoft(hue: number, theme: Theme): string {
  return theme === 'light' ? `hsl(${hue} 62% 40% / 0.12)` : `hsl(${hue} 70% 66% / 0.14)`;
}
export function customAccentGlow(hue: number, theme: Theme): string {
  return theme === 'light' ? `hsl(${hue} 62% 40% / 0.16)` : `hsl(${hue} 70% 66% / 0.18)`;
}

const THEME_KEY = 'marketpulse-theme';
const ACCENT_KEY = 'marketpulse-accent';
const CUSTOM_HUE_KEY = 'marketpulse-accent-hue';

function readTheme(): Theme {
  return localStorage.getItem(THEME_KEY) === 'light' ? 'light' : 'dark';
}

function readAccent(): Accent {
  const stored = localStorage.getItem(ACCENT_KEY);
  return stored === 'custom' || ACCENTS.some((a) => a.id === stored) ? (stored as Accent) : 'gold';
}

function readCustomHue(): number {
  const stored = Number(localStorage.getItem(CUSTOM_HUE_KEY));
  return Number.isFinite(stored) && stored >= 0 && stored <= 360 ? stored : 38;
}

// markets-ui and markets-admin run on separate origins and so cannot share localStorage - the
// profile menu's cross-link hands the chosen theme/accent/hue over as query params instead.
// Picked up once on load (initialTheme/initialAccent/initialCustomHue below), persisted locally
// from then on, then scrubbed from the URL by cleanParamsFromUrl.
function paramsTheme(params: URLSearchParams): Theme | null {
  const v = params.get('theme');
  return v === 'light' || v === 'dark' ? v : null;
}

function paramsAccent(params: URLSearchParams): Accent | null {
  const v = params.get('accent');
  return v === 'custom' || ACCENTS.some((a) => a.id === v) ? (v as Accent) : null;
}

function paramsCustomHue(params: URLSearchParams): number | null {
  if (!params.has('hue')) return null;
  const v = Number(params.get('hue'));
  return Number.isFinite(v) && v >= 0 && v <= 360 ? v : null;
}

export function initialTheme(): Theme {
  return paramsTheme(new URLSearchParams(location.search)) ?? readTheme();
}

export function initialAccent(): Accent {
  return paramsAccent(new URLSearchParams(location.search)) ?? readAccent();
}

export function initialCustomHue(): number {
  return paramsCustomHue(new URLSearchParams(location.search)) ?? readCustomHue();
}

export function cleanParamsFromUrl(): void {
  const params = new URLSearchParams(location.search);
  if (params.has('theme') || params.has('accent') || params.has('hue')) {
    history.replaceState(null, '', location.pathname + location.hash);
  }
}

export function applyTheme(theme: Theme): void {
  document.documentElement.setAttribute('data-theme', theme);
  localStorage.setItem(THEME_KEY, theme);
}

// Presets are plain CSS ([data-accent='...'] rules from tokens.ts); a custom hue can't be
// enumerated in CSS, so it's the one case that sets these vars directly on the host document.
export function applyAccent(accent: Accent, hue: number, theme: Theme): void {
  document.documentElement.setAttribute('data-accent', accent);
  localStorage.setItem(ACCENT_KEY, accent);
  localStorage.setItem(CUSTOM_HUE_KEY, String(hue));
  const root = document.documentElement.style;
  if (accent === 'custom') {
    root.setProperty('--accent', customAccentColor(hue, theme));
    root.setProperty('--accent-soft', customAccentSoft(hue, theme));
    root.setProperty('--glow', customAccentGlow(hue, theme));
  } else {
    root.removeProperty('--accent');
    root.removeProperty('--accent-soft');
    root.removeProperty('--glow');
  }
}

export function withState(href: string, theme: Theme, accent: Accent, hue: number): string {
  const url = new URL(href, location.href);
  url.searchParams.set('theme', theme);
  url.searchParams.set('accent', accent);
  url.searchParams.set('hue', String(hue));
  return url.toString();
}
