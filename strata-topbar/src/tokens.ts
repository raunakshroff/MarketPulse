// The Strata design tokens, single-sourced here so markets-ui and markets-admin can no longer
// drift the way two hand-copied <style> blocks eventually do. Injected once into the *host*
// document's <head> (not the shadow root) because every non-topbar element on the page - cards,
// tables, buttons - reads these same custom properties from :root.
const TOKENS_CSS = `
:root {
  --bg: #0a0a0c;
  --surface: #141417;
  --surface-2: #1c1c20;
  --line: rgba(255, 255, 255, 0.07);
  --line-strong: rgba(255, 255, 255, 0.13);
  --text: #f3f1ec;
  --muted: #a3a097;
  --faint: #6c6961;
  --accent: #d8b15f;
  --accent-soft: rgba(216, 177, 95, 0.14);
  --up: #56cf9b;
  --down: #f0876a;
  --up-soft: rgba(86, 207, 155, 0.12);
  --down-soft: rgba(240, 135, 106, 0.12);
  --shadow: 0 1px 0 rgba(255, 255, 255, 0.04), 0 24px 60px -34px rgba(0, 0, 0, 0.8);
  --glow: rgba(216, 177, 95, 0.18);
}

[data-theme='light'] {
  --bg: #f1eee7;
  --surface: #ffffff;
  --surface-2: #f9f7f1;
  --line: rgba(20, 18, 14, 0.08);
  --line-strong: rgba(20, 18, 14, 0.15);
  --text: #171511;
  --muted: #6c685f;
  --faint: #a39e93;
  --accent: #a9762b;
  --accent-soft: rgba(169, 118, 43, 0.12);
  --up: #138a5e;
  --down: #cf5a34;
  --up-soft: rgba(19, 138, 94, 0.1);
  --down-soft: rgba(207, 90, 52, 0.1);
  --shadow: 0 1px 0 rgba(255, 255, 255, 0.7), 0 26px 54px -36px rgba(40, 30, 10, 0.3);
  --glow: rgba(169, 118, 43, 0.16);
}

[data-accent='emerald'] { --accent: #4fd88f; --accent-soft: rgba(79, 216, 143, 0.14); --glow: rgba(79, 216, 143, 0.18); }
[data-theme='light'][data-accent='emerald'] { --accent: #128a5e; --accent-soft: rgba(18, 138, 94, 0.12); --glow: rgba(18, 138, 94, 0.16); }

[data-accent='azure'] { --accent: #5b9df0; --accent-soft: rgba(91, 157, 240, 0.14); --glow: rgba(91, 157, 240, 0.18); }
[data-theme='light'][data-accent='azure'] { --accent: #2f5fb0; --accent-soft: rgba(47, 95, 176, 0.12); --glow: rgba(47, 95, 176, 0.16); }

[data-accent='violet'] { --accent: #a98bef; --accent-soft: rgba(169, 139, 239, 0.14); --glow: rgba(169, 139, 239, 0.18); }
[data-theme='light'][data-accent='violet'] { --accent: #6b3fc4; --accent-soft: rgba(107, 63, 196, 0.12); --glow: rgba(107, 63, 196, 0.16); }

[data-accent='rose'] { --accent: #ef8aa6; --accent-soft: rgba(239, 138, 166, 0.14); --glow: rgba(239, 138, 166, 0.18); }
[data-theme='light'][data-accent='rose'] { --accent: #c23d64; --accent-soft: rgba(194, 61, 100, 0.12); --glow: rgba(194, 61, 100, 0.16); }

[data-accent='teal'] { --accent: #5fd0c9; --accent-soft: rgba(95, 208, 201, 0.14); --glow: rgba(95, 208, 201, 0.18); }
[data-theme='light'][data-accent='teal'] { --accent: #12806f; --accent-soft: rgba(18, 128, 111, 0.12); --glow: rgba(18, 128, 111, 0.16); }
`;

export function injectTokens(): void {
  if (document.getElementById('strata-tokens')) return;
  const style = document.createElement('style');
  style.id = 'strata-tokens';
  style.textContent = TOKENS_CSS;
  document.head.appendChild(style);
}
