type ThemeMode = "light" | "dark" | "system";
type ResolvedTheme = "light" | "dark";

const STORAGE_KEY = "athanor-theme-mode";
const DARK_MEDIA_QUERY = "(prefers-color-scheme: dark)";

function isThemeMode(value: string | null): value is ThemeMode {
  return value === "light" || value === "dark" || value === "system";
}

export function useThemeMode() {
  const themeMode = useState<ThemeMode>("ui:theme-mode", () => "system");
  const resolvedTheme = useState<ResolvedTheme>(
    "ui:resolved-theme",
    () => "light",
  );

  const resolveTheme = (mode: ThemeMode): ResolvedTheme => {
    if (!import.meta.client) {
      return "light";
    }

    if (mode === "system") {
      return window.matchMedia(DARK_MEDIA_QUERY).matches ? "dark" : "light";
    }

    return mode;
  };

  const applyTheme = (mode: ThemeMode) => {
    if (!import.meta.client) {
      return;
    }

    const next = resolveTheme(mode);
    resolvedTheme.value = next;

    const root = document.documentElement;
    root.classList.toggle("dark", next === "dark");
    root.style.colorScheme = next;
  };

  const setThemeMode = (mode: ThemeMode) => {
    themeMode.value = mode;
  };

  if (import.meta.client) {
    onMounted(() => {
      const savedMode = window.localStorage.getItem(STORAGE_KEY);
      if (isThemeMode(savedMode)) {
        themeMode.value = savedMode;
      }

      applyTheme(themeMode.value);
      window.localStorage.setItem(STORAGE_KEY, themeMode.value);

      const media = window.matchMedia(DARK_MEDIA_QUERY);
      const handleMediaChange = () => {
        if (themeMode.value === "system") {
          applyTheme("system");
        }
      };

      media.addEventListener("change", handleMediaChange);
      onBeforeUnmount(() => {
        media.removeEventListener("change", handleMediaChange);
      });
    });

    watch(themeMode, (mode) => {
      window.localStorage.setItem(STORAGE_KEY, mode);
      applyTheme(mode);
    });
  }

  return {
    themeMode,
    resolvedTheme,
    setThemeMode,
  };
}
