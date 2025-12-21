/**
 * Web implementation of CallBranding plugin
 * 
 * Provides no-op implementation for web platform
 */

import { WebPlugin } from '@capacitor/core';
import type { CallBrandingPlugin } from './CallBranding';

export class CallBrandingWeb extends WebPlugin implements CallBrandingPlugin {
  async initialize(): Promise<void> {
    // Web platform doesn't support native call handling
    console.log('CallBranding: Web platform - native call handling not available');
  }
  
  async syncBranding(): Promise<{ success: boolean; count?: number; error?: string }> {
    // Web platform - handled by brandingApi service
    return { success: true };
  }
  
  async testIncomingCall(): Promise<void> {
    // Web platform - can't test real calls
    console.log('CallBranding: Web platform - cannot test real incoming calls');
  }
}

