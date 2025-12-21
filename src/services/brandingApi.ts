/**
 * SecureNode Branding API Service
 * 
 * Handles API calls to lookup branding information for phone numbers
 */

export interface BrandingInfo {
  e164: string;
  brand_name: string | null;
  logo_url: string | null;
  call_reason: string | null;
}

export interface BrandingApiConfig {
  apiUrl?: string;
  apiKey: string;
}

const DEFAULT_API_URL = 'https://calls.securenode.io/api';

/**
 * Lookup branding information for a phone number
 */
export async function lookupBranding(
  e164: string,
  config: BrandingApiConfig
): Promise<BrandingInfo> {
  const apiUrl = config.apiUrl || DEFAULT_API_URL;
  const url = `${apiUrl}/mobile/branding/lookup?e164=${encodeURIComponent(e164)}`;

  try {
    const response = await fetch(url, {
      method: 'GET',
      headers: {
        'X-API-Key': config.apiKey,
        'Content-Type': 'application/json',
      },
    });

    if (!response.ok) {
      if (response.status === 401) {
        throw new Error('Invalid API key. Please check your API key in settings.');
      }
      throw new Error(`API error: ${response.status} ${response.statusText}`);
    }

    const data = await response.json();
    return data as BrandingInfo;
  } catch (error) {
    if (error instanceof Error) {
      throw error;
    }
    throw new Error('Failed to lookup branding information');
  }
}

/**
 * Record branding imprint (for billing)
 */
export async function recordImprint(
  e164: string,
  config: BrandingApiConfig
): Promise<{ success: boolean; imprint_id?: string }> {
  const apiUrl = config.apiUrl || DEFAULT_API_URL;
  const url = `${apiUrl}/mobile/branding/imprint`;

  try {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        phone_number_e164: e164,
        displayed_at: new Date().toISOString(),
      }),
    });

    if (!response.ok) {
      throw new Error(`API error: ${response.status} ${response.statusText}`);
    }

    return await response.json();
  } catch (error) {
    console.error('Failed to record imprint:', error);
    return { success: false };
  }
}

