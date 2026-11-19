import { defineConfig } from 'vite-plus';

export default defineConfig({
  lint: { rules: { 'eslint/no-debugger': 'warn' } },
  fmt: { singleQuote: false, semi: false },
});
