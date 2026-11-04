/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './src/**/*.{js,ts,jsx,tsx}',
  ],
  theme: {
    extend: {
      colors: {
        // Dark navy / slate base theme
        sentinel: {
          950: '#0a0e1a',
          900: '#0f1525',
          850: '#141b2e',
          800: '#1a2338',
          700: '#243049',
          600: '#2d3a56',
          500: '#3b4865',
          400: '#5a6783',
          300: '#8a96b0',
          200: '#b8c0d4',
          100: '#dfe4f0',
        },
        // Orange accent
        accent: {
          50: '#fff7ed',
          100: '#ffedd5',
          200: '#fed7aa',
          300: '#fdba74',
          400: '#fb923c',
          500: '#f97316',
          600: '#ea580c',
          700: '#c2410c',
          800: '#9a3412',
          900: '#7c2d12',
        },
        // Severity colors
        severity: {
          critical: '#dc2626',
          high: '#ea580c',
          medium: '#f59e0b',
          low: '#3b82f6',
          info: '#64748b',
        },
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'monospace'],
      },
    },
  },
  plugins: [],
}
