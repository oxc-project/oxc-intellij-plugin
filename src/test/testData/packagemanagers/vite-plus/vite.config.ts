import { defineConfig } from 'vite-plus';

export default defineConfig({
  fmt: {
    singleQuote: true,
  },
  lint: {
    rules: {
      'eslint/no-debugger': 'error',
    },
  },
});
