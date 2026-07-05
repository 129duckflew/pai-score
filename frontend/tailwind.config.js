/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js}'],
  theme: {
    extend: {
      colors: {
        ink: '#09110f',
        felt: '#0b3b2e',
        rail: '#123d36',
        moss: '#1c6b55',
        gold: '#f3c969',
        ember: '#ef6b4a',
        jade: '#35d399'
      },
      boxShadow: {
        glow: '0 0 32px rgba(53, 211, 153, 0.16)',
        gold: '0 14px 40px rgba(243, 201, 105, 0.18)'
      }
    }
  },
  plugins: []
}
