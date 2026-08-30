import { defineConfig } from 'vite';

// Single dependency-free IIFE: markets-admin loads it with a plain <script> tag (no build
// step of its own), and markets-ui vendors the same file into its public/ directory.
export default defineConfig({
  build: {
    lib: {
      entry: 'src/index.ts',
      formats: ['iife'],
      name: 'StrataTopbar',
      fileName: () => 'strata-topbar.js',
    },
    cssCodeSplit: false,
    emptyOutDir: true,
  },
});
