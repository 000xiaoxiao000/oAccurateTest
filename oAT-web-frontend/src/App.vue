<template>
  <RouterView />
  <AiFloatingAssistant v-if="route.name !== 'project-ai' && route.name !== 'login' && route.name !== 'register'" />
  <GlobalProbeAlertToasts />
  <AppDialogHost />
  <AppTooltipHost />
</template>

<script setup lang="ts">
import { RouterView, useRoute } from 'vue-router'

import AiFloatingAssistant from '@/components/AiFloatingAssistant.vue'
import AppDialogHost from '@/components/AppDialogHost.vue'
import AppTooltipHost from '@/components/AppTooltipHost.vue'
import GlobalProbeAlertToasts from '@/components/GlobalProbeAlertToasts.vue'

const route = useRoute()
</script>
