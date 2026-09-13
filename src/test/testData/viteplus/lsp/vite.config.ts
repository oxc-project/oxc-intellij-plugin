import { defineConfig } from 'vite-plus';

export default defineConfig({
  lint: {
    rules: {
      'eslint/no-debugger': 'error',
    },
  },
  fmt: {
    singleQuote: true,
    semi: false,
  },
});
