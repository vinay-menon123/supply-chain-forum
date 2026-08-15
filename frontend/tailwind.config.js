/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  darkMode: "class",
  theme: {
    extend: {
      fontFamily: {
        sans: ["Inter", "system-ui", "sans-serif"],
      },
      colors: {
        "bg-deep": "#020203",
        "bg-base": "#050506",
        "bg-elevated": "#0a0a0c",
        accent: "#5E6AD2",
        "accent-bright": "#6872D9",
      },
      keyframes: {
        "fade-in-up": {
          "0%": { opacity: "0", transform: "translateY(24px)" },
          "100%": { opacity: "1", transform: "translateY(0)" },
        },
        "fade-in": {
          "0%": { opacity: "0" },
          "100%": { opacity: "1" },
        },
        // Translate-only on purpose: these drive the big blurred aurora blobs, and
        // animating scale/rotate on a blur-[80px] layer re-rasterizes the whole blurred
        // texture every frame (the cause of desktop lag). Pure translate is a free
        // compositor move, so the drift stays but the paint cost disappears.
        float: {
          "0%, 100%": { transform: "translateY(0px)" },
          "50%": { transform: "translateY(-26px)" },
        },
        drift: {
          "0%": { transform: "translate(0px, 0px)" },
          "25%": { transform: "translate(38px, -28px)" },
          "50%": { transform: "translate(-24px, 24px)" },
          "75%": { transform: "translate(24px, 32px)" },
          "100%": { transform: "translate(0px, 0px)" },
        },
        "gradient-x": {
          "0%, 100%": { backgroundPosition: "0% 50%" },
          "50%": { backgroundPosition: "100% 50%" },
        },
        "pulse-glow": {
          "0%, 100%": { boxShadow: "0 0 24px rgba(99, 102, 241, 0.35)" },
          "50%": { boxShadow: "0 0 48px rgba(168, 85, 247, 0.55)" },
        },
        wiggle: {
          "0%, 100%": { transform: "rotate(-6deg)" },
          "50%": { transform: "rotate(6deg)" },
        },
      },
      animation: {
        "fade-in-up": "fade-in-up 0.7s ease-out both",
        "fade-in": "fade-in 1s ease-out both",
        float: "float 7s ease-in-out infinite",
        "float-slow": "float 11s ease-in-out infinite",
        drift: "drift 18s ease-in-out infinite",
        "drift-slow": "drift 28s ease-in-out infinite",
        "gradient-x": "gradient-x 6s ease infinite",
        "pulse-glow": "pulse-glow 3.5s ease-in-out infinite",
        wiggle: "wiggle 2.5s ease-in-out infinite",
      },
    },
  },
  plugins: [],
};
