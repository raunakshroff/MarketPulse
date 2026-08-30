(function(){function e(){if(document.getElementById(`strata-tokens`))return;let e=document.createElement(`style`);e.id=`strata-tokens`,e.textContent=`
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
`,document.head.appendChild(e)}var t=[{id:`gold`,label:`Gold`,swatch:`#d8b15f`},{id:`emerald`,label:`Emerald`,swatch:`#35c98a`},{id:`azure`,label:`Azure`,swatch:`#4f8fee`},{id:`violet`,label:`Violet`,swatch:`#9a7ce6`},{id:`rose`,label:`Rose`,swatch:`#ea7391`},{id:`teal`,label:`Teal`,swatch:`#3fc0b7`}];function n(e,t){return t===`light`?`hsl(${e} 62% 40%)`:`hsl(${e} 70% 66%)`}function r(e,t){return t===`light`?`hsl(${e} 62% 40% / 0.12)`:`hsl(${e} 70% 66% / 0.14)`}function i(e,t){return t===`light`?`hsl(${e} 62% 40% / 0.16)`:`hsl(${e} 70% 66% / 0.18)`}var a=`marketpulse-theme`,o=`marketpulse-accent`,s=`marketpulse-accent-hue`;function c(){return localStorage.getItem(a)===`light`?`light`:`dark`}function l(){let e=localStorage.getItem(o);return e===`custom`||t.some(t=>t.id===e)?e:`gold`}function u(){let e=Number(localStorage.getItem(s));return Number.isFinite(e)&&e>=0&&e<=360?e:38}function d(e){let t=e.get(`theme`);return t===`light`||t===`dark`?t:null}function f(e){let n=e.get(`accent`);return n===`custom`||t.some(e=>e.id===n)?n:null}function p(e){if(!e.has(`hue`))return null;let t=Number(e.get(`hue`));return Number.isFinite(t)&&t>=0&&t<=360?t:null}function m(){return d(new URLSearchParams(location.search))??c()}function h(){return f(new URLSearchParams(location.search))??l()}function g(){return p(new URLSearchParams(location.search))??u()}function _(){let e=new URLSearchParams(location.search);(e.has(`theme`)||e.has(`accent`)||e.has(`hue`))&&history.replaceState(null,``,location.pathname+location.hash)}function v(e){document.documentElement.setAttribute(`data-theme`,e),localStorage.setItem(a,e)}function y(e,t,a){document.documentElement.setAttribute(`data-accent`,e),localStorage.setItem(o,e),localStorage.setItem(s,String(t));let c=document.documentElement.style;e===`custom`?(c.setProperty(`--accent`,n(t,a)),c.setProperty(`--accent-soft`,r(t,a)),c.setProperty(`--glow`,i(t,a))):(c.removeProperty(`--accent`),c.removeProperty(`--accent-soft`),c.removeProperty(`--glow`))}function b(e,t,n,r){let i=new URL(e,location.href);return i.searchParams.set(`theme`,t),i.searchParams.set(`accent`,n),i.searchParams.set(`hue`,String(r)),i.toString()}var x=`<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="4.2"/><path d="M12 2v2.5M12 19.5V22M2 12h2.5M19.5 12H22M4.9 4.9l1.8 1.8M17.3 17.3l1.8 1.8M19.1 4.9l-1.8 1.8M6.7 17.3l-1.8 1.8"/></svg>`,S=`<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z"/></svg>`,C=`<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><path d="M15 3h6v6"/><path d="M10 14 21 3"/></svg>`,w=`
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
`;function T(e){return String(e??``).replace(/[&<>"']/g,e=>({"&":`&amp;`,"<":`&lt;`,">":`&gt;`,'"':`&quot;`,"'":`&#39;`})[e])}var E=class extends HTMLElement{static get observedAttributes(){return[`logo-href`,`badge`,`status-label`,`status-variant`,`menu-label`,`menu-href`,`avatar-initial`]}theme;accent;hue;paletteOpen=!1;profileOpen=!1;root;constructor(){super(),e(),this.theme=m(),this.accent=h(),this.hue=g(),this.root=this.attachShadow({mode:`open`})}connectedCallback(){v(this.theme),y(this.accent,this.hue,this.theme),_(),this.render()}attributeChangedCallback(){this.root.childNodes.length&&this.render()}attr(e,t=``){return this.getAttribute(e)??t}render(){let e=this.attr(`logo-href`,`/`),t=this.attr(`badge`),n=this.attr(`status-label`),r=this.attr(`status-variant`)===`down`,i=this.attr(`avatar-initial`,`A`);this.root.innerHTML=`
      <style>${w}</style>
      <div class="topbar">
        <div class="topbar-inner">
          <a class="logo" id="logo-link" href="${T(e)}">
            <svg width="26" height="26" viewBox="0 0 26 26" aria-hidden="true">
              <g fill="var(--accent)">
                <rect x="3" y="15.4" width="20" height="3.4" rx="1.7"></rect>
                <rect x="6" y="9.7" width="14" height="3.4" rx="1.7" opacity=".62"></rect>
                <rect x="9" y="4" width="8" height="3.4" rx="1.7" opacity=".38"></rect>
              </g>
            </svg>
            <span class="logo-title">Strata</span>
          </a>
          ${t?`<span class="pending-note">${T(t)}</span>`:``}
          <div class="center-slot"><slot name="center"></slot></div>
          <nav class="actions">
            ${n?`<div class="market-status${r?` down`:``}">
                     <span class="market-status-dot"></span>
                     <span class="status-text">${T(n)}</span>
                   </div>`:``}
            <slot name="actions"></slot>
            <button class="icon-btn" id="theme-toggle" title="Toggle theme" type="button">${this.theme===`dark`?S:x}</button>
            <div class="popover-anchor">
              <button class="icon-btn" id="accent-toggle" title="Accent color" type="button">
                <span style="width:16px;height:16px;border-radius:50%;background:var(--accent);display:block"></span>
              </button>
              ${this.paletteOpen?this.renderAccentPopover():``}
            </div>
            <div class="popover-anchor">
              <div class="avatar" id="avatar-toggle">${T(i)}</div>
              ${this.profileOpen?this.renderProfilePopover():``}
            </div>
          </nav>
        </div>
      </div>
    `,this.wireEvents()}renderAccentPopover(){return`
      <div class="backdrop" id="palette-backdrop"></div>
      <div class="accent-popover">
        <div class="accent-swatch-grid">${t.map(e=>`<button class="accent-swatch${this.accent===e.id?` active`:``}" data-accent="${e.id}" style="background:${e.swatch}" title="${T(e.label)}"></button>`).join(``)}</div>
        <div class="accent-popover-label">Custom</div>
        <div class="hue-slider" id="hue-slider">
          <span class="hue-slider-thumb" id="hue-thumb" style="left:${this.hue/360*100}%;background:hsl(${this.hue} 70% 60%)"></span>
        </div>
      </div>
    `}renderProfilePopover(){let e=this.attr(`menu-label`),t=this.attr(`menu-href`);return t?`
      <div class="backdrop" id="profile-backdrop"></div>
      <div class="profile-popover">
        <a class="profile-menu-item" id="menu-link" href="${T(b(t,this.theme,this.accent,this.hue))}">
          ${C}${T(e)}
        </a>
      </div>
    `:``}wireEvents(){this.root.getElementById(`logo-link`)?.addEventListener(`click`,e=>{let t=e;if(t.defaultPrevented||t.button!==0||t.metaKey||t.ctrlKey||t.shiftKey||t.altKey)return;let n=this.attr(`logo-href`,`/`);this.dispatchEvent(new CustomEvent(`strata-navigate`,{detail:{href:n},bubbles:!0,composed:!0,cancelable:!0}))||t.preventDefault()}),this.root.getElementById(`theme-toggle`)?.addEventListener(`click`,()=>{this.theme=this.theme===`dark`?`light`:`dark`,v(this.theme),y(this.accent,this.hue,this.theme),this.render()}),this.root.getElementById(`accent-toggle`)?.addEventListener(`click`,()=>{this.profileOpen=!1,this.paletteOpen=!this.paletteOpen,this.render()}),this.root.getElementById(`palette-backdrop`)?.addEventListener(`click`,()=>{this.paletteOpen=!1,this.render()}),this.root.querySelectorAll(`.accent-swatch`).forEach(e=>{e.addEventListener(`click`,()=>{this.accent=e.dataset.accent,y(this.accent,this.hue,this.theme),this.paletteOpen=!1,this.render()})});let e=this.root.getElementById(`hue-slider`);if(e){let t=t=>{let n=e.getBoundingClientRect(),r=Math.min(Math.max(t.clientX-n.left,0),n.width);return Math.round(r/n.width*360)},n=()=>{let e=this.root.getElementById(`hue-thumb`);e&&(e.style.left=`${this.hue/360*100}%`,e.style.background=`hsl(${this.hue} 70% 60%)`),this.root.querySelectorAll(`.accent-swatch.active`).forEach(e=>e.classList.remove(`active`))};e.addEventListener(`pointerdown`,r=>{e.setPointerCapture(r.pointerId),this.accent=`custom`,this.hue=t(r),y(this.accent,this.hue,this.theme),n()}),e.addEventListener(`pointermove`,e=>{e.buttons===1&&(this.hue=t(e),y(this.accent,this.hue,this.theme),n())})}this.root.getElementById(`avatar-toggle`)?.addEventListener(`click`,()=>{this.paletteOpen=!1,this.profileOpen=!this.profileOpen,this.render()}),this.root.getElementById(`profile-backdrop`)?.addEventListener(`click`,()=>{this.profileOpen=!1,this.render()})}};customElements.define(`strata-topbar`,E)})();