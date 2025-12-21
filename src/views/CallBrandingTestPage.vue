<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <ion-title>Call Branding Test</ion-title>
      </ion-toolbar>
    </ion-header>
    <ion-content :fullscreen="true">
      <ion-header collapse="condense">
        <ion-toolbar>
          <ion-title size="large">Call Branding Test</ion-title>
        </ion-toolbar>
      </ion-header>

      <div class="ion-padding">
        <!-- API Configuration -->
        <ion-card>
          <ion-card-header>
            <ion-card-title>API Configuration</ion-card-title>
          </ion-card-header>
          <ion-card-content>
            <ion-item>
              <ion-label position="stacked">API Key</ion-label>
              <ion-input
                v-model="apiKey"
                type="password"
                placeholder="Enter your API key"
              ></ion-input>
            </ion-item>
            <ion-item>
              <ion-label position="stacked">API URL (optional)</ion-label>
              <ion-input
                v-model="apiUrl"
                placeholder="https://calls.securenode.io/api"
              ></ion-input>
            </ion-item>
          </ion-card-content>
        </ion-card>

        <!-- Test Input -->
        <ion-card>
          <ion-card-header>
            <ion-card-title>Test Phone Number</ion-card-title>
          </ion-card-header>
          <ion-card-content>
            <ion-item>
              <ion-label position="stacked">Phone Number (E.164 format)</ion-label>
              <ion-input
                v-model="testPhoneNumber"
                placeholder="+1234567890"
                type="tel"
              ></ion-input>
            </ion-item>
            <ion-button
              expand="block"
              :disabled="!apiKey || !testPhoneNumber || loading"
              @click="testBranding"
            >
              <ion-spinner v-if="loading" name="crescent"></ion-spinner>
              <span v-else>Test Branding</span>
            </ion-button>
          </ion-card-content>
        </ion-card>

        <!-- Error Display -->
        <ion-card v-if="error" color="danger">
          <ion-card-content>
            <ion-text color="light">
              <p>{{ error }}</p>
            </ion-text>
          </ion-card-content>
        </ion-card>

        <!-- Call Screen Preview -->
        <ion-card v-if="brandingInfo" class="call-screen-preview">
          <ion-card-header>
            <ion-card-title>Call Screen Preview</ion-card-title>
            <ion-card-subtitle>How this will appear on mobile</ion-card-subtitle>
          </ion-card-header>
          <ion-card-content>
            <div class="call-screen">
              <!-- Logo -->
              <div class="call-logo-container" v-if="brandingInfo.logo_url">
                <img
                  :src="brandingInfo.logo_url"
                  :alt="brandingInfo.brand_name || 'Logo'"
                  class="call-logo"
                  @error="logoError = true"
                  v-if="!logoError"
                />
                <div v-else class="call-logo-placeholder">
                  <ion-icon :icon="business" size="large"></ion-icon>
                </div>
              </div>
              <div v-else class="call-logo-placeholder">
                <ion-icon :icon="business" size="large"></ion-icon>
              </div>

              <!-- Brand Name -->
              <h1 class="call-brand-name">
                {{ brandingInfo.brand_name || testPhoneNumber }}
              </h1>

              <!-- Phone Number -->
              <p class="call-phone-number">{{ testPhoneNumber }}</p>

              <!-- Call Reason -->
              <p v-if="brandingInfo.call_reason" class="call-reason">
                {{ brandingInfo.call_reason }}
              </p>

              <!-- Call Buttons (simulated) -->
              <div class="call-buttons">
                <ion-button color="danger" shape="round" class="call-button">
                  <ion-icon :icon="close" slot="icon-only"></ion-icon>
                </ion-button>
                <ion-button color="success" shape="round" class="call-button">
                  <ion-icon :icon="call" slot="icon-only"></ion-icon>
                </ion-button>
              </div>
            </div>
          </ion-card-content>
        </ion-card>

        <!-- Branding Info Details -->
        <ion-card v-if="brandingInfo">
          <ion-card-header>
            <ion-card-title>Branding Details</ion-card-title>
          </ion-card-header>
          <ion-card-content>
            <ion-list>
              <ion-item>
                <ion-label>
                  <h2>Brand Name</h2>
                  <p>{{ brandingInfo.brand_name || 'Not set' }}</p>
                </ion-label>
              </ion-item>
              <ion-item>
                <ion-label>
                  <h2>Logo URL</h2>
                  <p>{{ brandingInfo.logo_url || 'Not set' }}</p>
                </ion-label>
              </ion-item>
              <ion-item>
                <ion-label>
                  <h2>Call Reason</h2>
                  <p>{{ brandingInfo.call_reason || 'Not set' }}</p>
                </ion-label>
              </ion-item>
            </ion-list>
          </ion-card-content>
        </ion-card>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import {
  IonPage,
  IonHeader,
  IonToolbar,
  IonTitle,
  IonContent,
  IonCard,
  IonCardHeader,
  IonCardTitle,
  IonCardSubtitle,
  IonCardContent,
  IonItem,
  IonLabel,
  IonInput,
  IonButton,
  IonSpinner,
  IonText,
  IonIcon,
  IonList,
} from '@ionic/vue';
import { ref, watch, onMounted } from 'vue';
import { business, call, close } from 'ionicons/icons';
import { lookupBranding, recordImprint, type BrandingInfo } from '@/services/brandingApi';
import CallBranding from '@/plugins/CallBranding';
import { Capacitor } from '@capacitor/core';

const apiKey = ref('');
const apiUrl = ref('https://calls.securenode.io/api');
const testPhoneNumber = ref('');
const brandingInfo = ref<BrandingInfo | null>(null);
const loading = ref(false);
const error = ref('');
const logoError = ref(false);
const sdkInitialized = ref(false);

// Initialize SDK on mount if on native platform
async function initializeSDK() {
  if (Capacitor.isNativePlatform() && apiKey.value) {
    try {
      await CallBranding.initialize({
        apiKey: apiKey.value,
        apiUrl: apiUrl.value || undefined,
      });
      sdkInitialized.value = true;
      
      // Sync branding data
      await CallBranding.syncBranding();
    } catch (err) {
      console.error('Failed to initialize SDK:', err);
    }
  }
}

// Watch for API key changes to re-initialize
watch(apiKey, (newKey) => {
  if (newKey && Capacitor.isNativePlatform() && !sdkInitialized.value) {
    initializeSDK();
  }
});

onMounted(() => {
  if (Capacitor.isNativePlatform()) {
    initializeSDK();
  }
});

async function testBranding() {
  if (!apiKey.value || !testPhoneNumber.value) {
    error.value = 'Please enter both API key and phone number';
    return;
  }

  // Validate E.164 format (basic check)
  if (!testPhoneNumber.value.startsWith('+')) {
    error.value = 'Phone number must be in E.164 format (e.g., +1234567890)';
    return;
  }

  loading.value = true;
  error.value = '';
  brandingInfo.value = null;
  logoError.value = false;

  try {
    const result = await lookupBranding(testPhoneNumber.value, {
      apiKey: apiKey.value,
      apiUrl: apiUrl.value || undefined,
    });

    brandingInfo.value = result;

    // Record imprint for billing
    await recordImprint(testPhoneNumber.value, {
      apiKey: apiKey.value,
      apiUrl: apiUrl.value || undefined,
    });
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'Failed to lookup branding';
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.call-screen-preview {
  margin-top: 20px;
}

.call-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 40px 20px;
  text-align: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 16px;
  color: white;
  min-height: 400px;
  justify-content: space-between;
}

.call-logo-container {
  margin-bottom: 20px;
}

.call-logo {
  width: 120px;
  height: 120px;
  border-radius: 60px;
  object-fit: cover;
  border: 4px solid rgba(255, 255, 255, 0.3);
  background: white;
}

.call-logo-placeholder {
  width: 120px;
  height: 120px;
  border-radius: 60px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 20px;
  border: 4px solid rgba(255, 255, 255, 0.3);
}

.call-brand-name {
  font-size: 28px;
  font-weight: 600;
  margin: 20px 0 10px;
  color: white;
}

.call-phone-number {
  font-size: 18px;
  opacity: 0.9;
  margin: 0 0 10px;
}

.call-reason {
  font-size: 16px;
  opacity: 0.8;
  margin: 10px 0 30px;
  font-style: italic;
}

.call-buttons {
  display: flex;
  gap: 40px;
  margin-top: 40px;
}

.call-button {
  width: 64px;
  height: 64px;
  --border-radius: 50%;
}
</style>

