/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      // 对齐 prototypes/styles.css 的设计 token
      // M1.5（UI 系统改造）将扩展更多配色与字体
      colors: {
        // 主色：深海蓝
        finrpa: {
          blue: '#1A3A5C',
          'blue-light': '#2A5A8C',
          'blue-dark': '#0F2440',
          gold: '#C9A84C',
          'gold-light': '#DFC474',
          'gold-dark': '#A68A30',
        },
        // 状态色
        status: {
          running: '#3B82F6',
          completed: '#10B981',
          failed: '#EF4444',
          pending: '#C9A84C',
          'needs-human': '#F97316',
          paused: '#8B5CF6',
          queued: '#6B7280',
          timeout: '#DC2626',
        },
      },
      fontFamily: {
        sans: [
          'Inter',
          '-apple-system',
          'BlinkMacSystemFont',
          'PingFang SC',
          'Microsoft YaHei',
          'sans-serif',
        ],
        mono: ['SF Mono', 'JetBrains Mono', 'Consolas', 'monospace'],
      },
      borderRadius: {
        sm: '6px',
        md: '10px',
        lg: '16px',
        xl: '24px',
      },
    },
  },
  plugins: [],
}
