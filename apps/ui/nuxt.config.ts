export default defineNuxtConfig({
  compatibilityDate: "2026-03-05",
  devtools: { enabled: false },
  modules: ["@nuxtjs/tailwindcss", "shadcn-nuxt"],
  css: ["~/assets/css/tailwind.css"],
  shadcn: {
    prefix: "",
    componentDir: "./components/ui",
  },
  runtimeConfig: {
    apiInternalBaseUrl: "http://localhost:8080",
    public: {
      apiBaseUrl: "http://localhost:8080",
    },
  },
});
