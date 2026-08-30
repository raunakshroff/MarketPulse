import { injectTokens } from './tokens';
import {
  ACCENTS,
  type Accent,
  type Theme,
  initialTheme,
  initialAccent,
  initialCustomHue,
  cleanParamsFromUrl,
  applyTheme,
  applyAccent,
  withState,
} from './state';
import { SUN_ICON, MOON_ICON, LINK_ICON } from './icons';

// Ported verbatim from markets-ui/src/theme.css's topbar-related rules. Custom properties
// (--accent, --surface, ...) pierce the shadow boundary by inheritance, so this can lean on
// tokens.ts's injected :root/[data-theme]/[data-accent] rules exactly like the rest of the page.
const STYLE = `
  :host { display: block; font-family: 'Space Grotesk', sans-serif; }
  * { box-sizing: border-box; }
  .topbar {
    position: sticky;
    top: 0;
    z-index: 40;
    backdrop-filter: blur(18px);
    background: color-mix(in srgb, var(--bg) 78%, transparent);
    border-bottom: 1px solid var(--line);
  }
  .topbar-inner {
    max-width: 1320px;
    margin: 0 auto;
    padding: 0 clamp(16px, 3vw, 32px);
    height: 66px;
    display: flex;
    align-items: center;
    gap: clamp(14px, 2.4vw, 32px);
  }
  .logo { display: flex; align-items: center; gap: 11px; cursor: pointer; flex: none; color: var(--text); text-decoration: none; }
  .logo-title { font-family: 'Instrument Serif', serif; font-size: 25px; letter-spacing: 0.01em; line-height: 1; }
  .pending-note {
    display: inline-flex; align-items: center; gap: 6px; font-size: 11.5px; color: var(--faint);
    border: 1px dashed var(--line-strong); border-radius: 8px; padding: 4px 9px; flex: none;
  }
  .center-slot { flex: 1; min-width: 0; max-width: 480px; }
  .actions { display: flex; align-items: center; gap: 6px; margin-left: auto; flex: none; }
  .market-status { display: flex; align-items: center; gap: 7px; padding: 7px 12px; border-radius: 10px; background: var(--up-soft); margin-right: 4px; }
  .market-status.down { background: var(--down-soft); }
  .market-status-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--up); }
  .market-status.down .market-status-dot { background: var(--down); }
  .status-text { font-size: 12px; font-weight: 500; color: var(--up); }
  .market-status.down .status-text { color: var(--down); }
  .icon-btn {
    width: 40px; height: 40px; border-radius: 11px; border: 1px solid var(--line); background: var(--surface);
    color: var(--text); cursor: pointer; display: flex; align-items: center; justify-content: center; position: relative;
  }
  .icon-btn:hover { border-color: var(--line-strong); }
  .popover-anchor { position: relative; }
  .backdrop { position: fixed; inset: 0; z-index: 50; }
  @keyframes fadeUp { from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: none; } }
  .accent-popover {
    position: absolute; top: 50px; right: 0; width: 200px; background: var(--surface); border: 1px solid var(--line-strong);
    border-radius: 16px; box-shadow: var(--shadow); padding: 14px; z-index: 60; animation: fadeUp 0.18s ease both;
  }
  .accent-swatch-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
  .accent-popover-label { font-size: 10.5px; letter-spacing: 0.12em; text-transform: uppercase; color: var(--faint); margin: 14px 0 10px; }
  .hue-slider {
    position: relative; height: 14px; border-radius: 999px; border: 1px solid var(--line-strong); cursor: pointer;
    background: linear-gradient(to right, hsl(0 90% 60%), hsl(60 90% 60%), hsl(120 90% 60%), hsl(180 90% 60%), hsl(240 90% 60%), hsl(300 90% 60%), hsl(360 90% 60%));
    touch-action: none;
  }
  .hue-slider-thumb {
    position: absolute; top: 50%; width: 18px; height: 18px; border-radius: 50%; border: 2px solid #fff;
    box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.35), 0 2px 6px rgba(0, 0, 0, 0.4); transform: translate(-50%, -50%); pointer-events: none;
  }
  .accent-swatch { appearance: none; width: 32px; height: 32px; border-radius: 50%; border: 2px solid var(--surface); outline: 1px solid var(--line); cursor: pointer; padding: 0; }
  .accent-swatch:hover { outline-color: var(--line-strong); }
  .accent-swatch.active { outline: 2px solid var(--text); outline-offset: 1px; }
  .avatar {
    width: 40px; height: 40px; border-radius: 50%;
    background: linear-gradient(135deg, var(--accent), color-mix(in srgb, var(--accent) 40%, var(--text)));
    display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 14px; color: var(--bg); cursor: pointer; flex: none;
  }
  .profile-popover {
    position: absolute; top: 50px; right: 0; width: 190px; background: var(--surface); border: 1px solid var(--line-strong);
    border-radius: 16px; box-shadow: var(--shadow); padding: 6px; z-index: 60; animation: fadeUp 0.18s ease both;
  }
  .profile-menu-item {
    display: flex; align-items: center; gap: 10px; padding: 10px 12px; border-radius: 11px; font-size: 13.5px;
    font-weight: 500; color: var(--text); text-decoration: none; cursor: pointer;
  }
  .profile-menu-item:hover { background: var(--surface-2); }
`;

function esc(s: string): string {
  return String(s ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c] as string,
  );
}

export class StrataTopbar extends HTMLElement {
  static get observedAttributes(): string[] {
    return ['logo-href', 'badge', 'status-label', 'status-variant', 'menu-label', 'menu-href', 'avatar-initial'];
  }

  private theme: Theme;
  private accent: Accent;
  private hue: number;
  private paletteOpen = false;
  private profileOpen = false;
  private readonly root: ShadowRoot;

  constructor() {
    super();
    injectTokens();
    this.theme = initialTheme();
    this.accent = initialAccent();
    this.hue = initialCustomHue();
    this.root = this.attachShadow({ mode: 'open' });
  }

  connectedCallback(): void {
    applyTheme(this.theme);
    applyAccent(this.accent, this.hue, this.theme);
    cleanParamsFromUrl();
    this.render();
  }

  attributeChangedCallback(): void {
    if (this.root.childNodes.length) this.render();
  }

  private attr(name: string, fallback = ''): string {
    return this.getAttribute(name) ?? fallback;
  }

  private render(): void {
    const logoHref = this.attr('logo-href', '/');
    const badge = this.attr('badge');
    const statusLabel = this.attr('status-label');
    const statusDown = this.attr('status-variant') === 'down';
    const avatarInitial = this.attr('avatar-initial', 'A');

    this.root.innerHTML = `
      <style>${STYLE}</style>
      <div class="topbar">
        <div class="topbar-inner">
          <a class="logo" id="logo-link" href="${esc(logoHref)}">
            <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden="true">
              <g fill="var(--accent)">
                <rect x="3" y="15.4" width="20" height="3.4" rx="1.7"></rect>
                <rect x="6" y="9.7" width="14" height="3.4" rx="1.7" opacity=".62"></rect>
                <rect x="9" y="4" width="8" height="3.4" rx="1.7" opacity=".38"></rect>
              </g>
            </svg>
            <span class="logo-title">Strata</span>
          </a>
          ${badge ? `<span class="pending-note">${esc(badge)}</span>` : ''}
          <div class="center-slot"><slot name="center"></slot></div>
          <nav class="actions">
            ${
              statusLabel
                ? `<div class="market-status${statusDown ? ' down' : ''}">
                     <span class="market-status-dot"></span>
                     <span class="status-text">${esc(statusLabel)}</span>
                   </div>`
                : ''
            }
            <slot name="actions"></slot>
            <button class="icon-btn" id="theme-toggle" title="Toggle theme" type="button">${this.theme === 'dark' ? MOON_ICON : SUN_ICON}</button>
            <div class="popover-anchor">
              <button class="icon-btn" id="accent-toggle" title="Accent color" type="button">
                <span style="width:16px;height:16px;border-radius:50%;background:var(--accent);display:block"></span>
              </button>
              ${this.paletteOpen ? this.renderAccentPopover() : ''}
            </div>
            <div class="popover-anchor">
              <div class="avatar" id="avatar-toggle">${esc(avatarInitial)}</div>
              ${this.profileOpen ? this.renderProfilePopover() : ''}
            </div>
          </nav>
        </div>
      </div>
    `;

    this.wireEvents();
  }

  private renderAccentPopover(): string {
    const swatches = ACCENTS.map(
      (a) =>
        `<button class="accent-swatch${this.accent === a.id ? ' active' : ''}" data-accent="${a.id}" style="background:${a.swatch}" title="${esc(a.label)}"></button>`,
    ).join('');
    return `
      <div class="backdrop" id="palette-backdrop"></div>
      <div class="accent-popover">
        <div class="accent-swatch-grid">${swatches}</div>
        <div class="accent-popover-label">Custom</div>
        <div class="hue-slider" id="hue-slider">
          <span class="hue-slider-thumb" id="hue-thumb" style="left:${(this.hue / 360) * 100}%;background:hsl(${this.hue} 70% 60%)"></span>
        </div>
      </div>
    `;
  }

  private renderProfilePopover(): string {
    const label = this.attr('menu-label');
    const href = this.attr('menu-href');
    if (!href) return '';
    const target = withState(href, this.theme, this.accent, this.hue);
    return `
      <div class="backdrop" id="profile-backdrop"></div>
      <div class="profile-popover">
        <a class="profile-menu-item" id="menu-link" href="${esc(target)}" target="_blank" rel="noopener noreferrer">
          ${LINK_ICON}${esc(label)}
        </a>
      </div>
    `;
  }

  private wireEvents(): void {
    // A React (or other client-router) host can't intercept a plain shadow-DOM <a> click, so a
    // same-app logo link would otherwise force a full page reload instead of a client-side nav.
    // Offer the host a cancelable escape hatch; if nothing listens, the anchor navigates normally
    // (which is exactly right for a cross-origin logo-href, e.g. markets-admin linking back to
    // markets-ui).
    this.root.getElementById('logo-link')?.addEventListener('click', (e) => {
      const evt = e as MouseEvent;
      if (evt.defaultPrevented || evt.button !== 0 || evt.metaKey || evt.ctrlKey || evt.shiftKey || evt.altKey) {
        return;
      }
      const href = this.attr('logo-href', '/');
      const notCanceled = this.dispatchEvent(
        new CustomEvent('strata-navigate', { detail: { href }, bubbles: true, composed: true, cancelable: true }),
      );
      if (!notCanceled) evt.preventDefault();
    });

    this.root.getElementById('theme-toggle')?.addEventListener('click', () => {
      this.theme = this.theme === 'dark' ? 'light' : 'dark';
      applyTheme(this.theme);
      applyAccent(this.accent, this.hue, this.theme);
      this.render();
    });

    this.root.getElementById('accent-toggle')?.addEventListener('click', () => {
      this.profileOpen = false;
      this.paletteOpen = !this.paletteOpen;
      this.render();
    });

    this.root.getElementById('palette-backdrop')?.addEventListener('click', () => {
      this.paletteOpen = false;
      this.render();
    });

    this.root.querySelectorAll<HTMLButtonElement>('.accent-swatch').forEach((el) => {
      el.addEventListener('click', () => {
        this.accent = el.dataset.accent as Accent;
        applyAccent(this.accent, this.hue, this.theme);
        this.paletteOpen = false;
        this.render();
      });
    });

    const slider = this.root.getElementById('hue-slider');
    if (slider) {
      const hueFromEvent = (e: PointerEvent): number => {
        const rect = slider.getBoundingClientRect();
        const x = Math.min(Math.max(e.clientX - rect.left, 0), rect.width);
        return Math.round((x / rect.width) * 360);
      };
      // Dragging patches the thumb/vars directly instead of calling render(): a full re-render
      // would destroy and recreate `slider`, which drops the pointer capture set on pointerdown
      // and breaks the drag after the first move.
      const patch = () => {
        const thumb = this.root.getElementById('hue-thumb');
        if (thumb) {
          thumb.style.left = `${(this.hue / 360) * 100}%`;
          thumb.style.background = `hsl(${this.hue} 70% 60%)`;
        }
        this.root.querySelectorAll('.accent-swatch.active').forEach((el) => el.classList.remove('active'));
      };
      slider.addEventListener('pointerdown', (e) => {
        slider.setPointerCapture(e.pointerId);
        this.accent = 'custom';
        this.hue = hueFromEvent(e);
        applyAccent(this.accent, this.hue, this.theme);
        patch();
      });
      slider.addEventListener('pointermove', (e) => {
        if (e.buttons !== 1) return;
        this.hue = hueFromEvent(e);
        applyAccent(this.accent, this.hue, this.theme);
        patch();
      });
    }

    this.root.getElementById('avatar-toggle')?.addEventListener('click', () => {
      this.paletteOpen = false;
      this.profileOpen = !this.profileOpen;
      this.render();
    });

    this.root.getElementById('profile-backdrop')?.addEventListener('click', () => {
      this.profileOpen = false;
      this.render();
    });

    this.root.getElementById('menu-link')?.addEventListener('click', () => {
      // Deferred: re-rendering synchronously would remove this <a> mid-click, before the
      // browser has resolved its target="_blank" navigation.
      setTimeout(() => {
        this.profileOpen = false;
        this.render();
      }, 0);
    });
  }
}

customElements.define('strata-topbar', StrataTopbar);
