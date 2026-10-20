import { defineConfig } from 'vitest/config';
export default defineConfig({base:'./',server:{host:'127.0.0.1'},test:{include:['tests/**/*.test.ts']}});
