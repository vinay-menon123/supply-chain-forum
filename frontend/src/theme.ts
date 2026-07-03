export function initTheme(): boolean {
  const stored = localStorage.getItem("theme");
  const dark = stored
    ? stored === "dark"
    : window.matchMedia("(prefers-color-scheme: dark)").matches;
  document.documentElement.classList.toggle("dark", dark);
  return dark;
}

export function setTheme(dark: boolean) {
  localStorage.setItem("theme", dark ? "dark" : "light");
  document.documentElement.classList.toggle("dark", dark);
}

export function isDark(): boolean {
  return document.documentElement.classList.contains("dark");
}
