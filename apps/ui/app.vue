<script setup lang="ts">
useHead({
    script: [
        {
            key: "theme-init",
            tagPosition: "head",
            innerHTML:
                "(function(){try{var k='athanor-theme-mode';var t=localStorage.getItem(k)||'system';var d=t==='dark'||(t==='system'&&window.matchMedia('(prefers-color-scheme: dark)').matches);var r=document.documentElement;r.classList.toggle('dark',d);r.style.colorScheme=d?'dark':'light';}catch(e){}})();",
        },
    ],
});
</script>

<template>
    <NuxtLayout>
        <NuxtPage />
    </NuxtLayout>
</template>
