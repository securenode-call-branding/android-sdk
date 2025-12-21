//
//  CallBrandingPlugin.swift
//  SecureNode Test
//
//  Capacitor plugin for Call Branding
//

import Foundation
import Capacitor

/**
 * Capacitor plugin to bridge JavaScript to native call branding
 */
@objc(CallBrandingPlugin)
public class CallBrandingPlugin: CAPPlugin {
    
    @objc func initialize(_ call: CAPPluginCall) {
        guard let apiKey = call.getString("apiKey") else {
            call.reject("API key is required")
            return
        }
        
        let apiUrl = call.getString("apiUrl") ?? "https://calls.securenode.io/api"
        
        CallKitManager.shared.initialize(apiKey: apiKey, apiUrl: apiUrl)
        
        // Store API key for persistence
        UserDefaults.standard.set(apiKey, forKey: "securenode_api_key")
        UserDefaults.standard.set(apiUrl, forKey: "securenode_api_url")
        
        call.resolve()
    }
    
    @objc func syncBranding(_ call: CAPPluginCall) {
        CallKitManager.shared.syncBrandingData()
        call.resolve([
            "success": true
        ])
    }
    
    @objc func testIncomingCall(_ call: CAPPluginCall) {
        guard let phoneNumber = call.getString("phoneNumber") else {
            call.reject("Phone number is required")
            return
        }
        
        let uuid = UUID()
        CallKitManager.shared.handleIncomingCall(uuid: uuid, phoneNumber: phoneNumber)
        
        call.resolve()
    }
}

