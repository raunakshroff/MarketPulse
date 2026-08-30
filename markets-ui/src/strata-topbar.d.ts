import type { DetailedHTMLProps, HTMLAttributes } from 'react';

// Registered globally by public/strata-topbar.js (loaded via a plain <script> tag in index.html,
// vendored from the strata-topbar package so markets-ui and markets-admin share one topbar
// implementation instead of two hand-maintained copies).
declare module 'react' {
  namespace JSX {
    interface IntrinsicElements {
      'strata-topbar': DetailedHTMLProps<HTMLAttributes<HTMLElement>, HTMLElement> & {
        'logo-href'?: string;
        badge?: string;
        'status-label'?: string;
        'status-variant'?: 'up' | 'down';
        'menu-label'?: string;
        'menu-href'?: string;
        'avatar-initial'?: string;
      };
    }
  }
}
