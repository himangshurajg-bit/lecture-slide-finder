import { defineConfig } from 'vitest/config';
export default defineConfig({base:'./',build:{target:'es2017'},server:{host:'127.0.0.1'},test:{include:['tests/**/*.test.ts']}});
