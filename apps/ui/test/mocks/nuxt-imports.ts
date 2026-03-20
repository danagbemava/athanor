import { ref } from "vue";

const stateStore = new Map<string, ReturnType<typeof ref>>();

export function useState<T>(key: string, init: () => T) {
  if (!stateStore.has(key)) {
    stateStore.set(key, ref(init()));
  }

  return stateStore.get(key) as ReturnType<typeof ref<T>>;
}

export function useRuntimeConfig() {
  return {
    public: {
      apiBaseUrl: "http://127.0.0.1:8080",
    },
  };
}
