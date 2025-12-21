/**
 * Capacitor Plugin for Call Branding
 * 
 * Bridges JavaScript to native call handling
 */

import { registerPlugin } from '@capacitor/core';

export interface CallBrandingPlugin {
  /**
   * Initialize the call branding SDK
   */
  initialize(options: { apiKey: string; apiUrl?: string }): Promise<void>;
  
  /**
   * Sync branding data from API
   */
  syncBranding(): Promise<{ success: boolean; count?: number; error?: string }>;
  
  /**
   * Test incoming call (for development)
   */
  testIncomingCall(options: { phoneNumber: string }): Promise<void>;
}

const CallBranding = registerPlugin<CallBrandingPlugin>('CallBranding', {
  web: () => import('./CallBranding.web').then(m => new m.CallBrandingWeb()),
});

export default CallBranding;

