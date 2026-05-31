<template>
  <RouterView />
  <GlobalProbeAlertToasts />
  <AppDialogHost />
</template>

<script setup lang="ts">
import { RouterView } from 'vue-router'

import AppDialogHost from '@/components/AppDialogHost.vue'
import GlobalProbeAlertToasts from '@/components/GlobalProbeAlertToasts.vue'
</script>
