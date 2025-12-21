/**
 * Call Handler Service
 * 
 * Bridges to native call handling for iOS and Android
 */

import { Capacitor } from '@capacitor/core';

export interface CallBrandingConfig {
  apiKey: string;
  apiUrl?: string;
}

/**
 * Initialize the call branding system
 */
export async function initializeCallBranding(config: CallBrandingConfig): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    // Call native plugin
    const CallBranding = await import('@/plugins/CallBranding');
    await CallBranding.default.initialize({
      apiKey: config.apiKey,
      apiUrl: config.apiUrl,
    });
    return;
  }
  // Web platform - no native call handling
  return Promise.resolve();
}

/**
 * Sync branding data from API
 */
export async function syncBrandingData(config: CallBrandingConfig): Promise<void> {
  if (Capacitor.isNativePlatform()) {
    // Call native plugin to sync
    const CallBranding = await import('@/plugins/CallBranding');
    await CallBranding.default.syncBranding();
    return;
  }
  // Web platform - handled by brandingApi service
  return Promise.resolve();
}

