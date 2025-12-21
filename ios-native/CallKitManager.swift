//
//  CallKitManager.swift
//  SecureNode Test
//
//  iOS CallKit integration for SecureNode branding
//

import Foundation
import CallKit
import UserNotifications

class CallKitManager: NSObject {
    private let provider: CXProvider
    private let callController = CXCallController()
    private var secureNode: SecureNodeSDK?
    private var activeCalls: [UUID: String] = [:]
    
    static let shared = CallKitManager()
    
    override init() {
        // Configure CallKit provider
        let configuration = CXProviderConfiguration(localizedName: "SecureNode")
        configuration.supportsVideo = false
        configuration.maximumCallsPerCallGroup = 1
        configuration.supportedHandleTypes = [.phoneNumber]
        configuration.iconTemplateImageData = UIImage(named: "AppIcon")?.pngData()
        
        provider = CXProvider(configuration: configuration)
        
        super.init()
        provider.setDelegate(self, queue: nil)
    }
    
    func initialize(apiKey: String, apiUrl: String = "https://calls.securenode.io/api") {
        guard let url = URL(string: apiUrl) else { return }
        
        let config = SecureNodeConfig(apiURL: url, apiKey: apiKey)
        secureNode = SecureNodeSDK(config: config)
        
        // Initial sync on startup
        syncBrandingData()
    }
    
    func syncBrandingData() {
        secureNode?.syncBranding { result in
            switch result {
            case .success(let response):
                print("Synced \(response.branding.count) branding records")
            case .failure(let error):
                print("Sync failed: \(error)")
            }
        }
    }
    
    // Called when VoIP push notification arrives or call is detected
    func handleIncomingCall(uuid: UUID, phoneNumber: String) {
        activeCalls[uuid] = phoneNumber
        
        let update = CXCallUpdate()
        update.remoteHandle = CXHandle(type: .phoneNumber, value: phoneNumber)
        update.localizedCallerName = phoneNumber // Default to phone number
        
        // Lookup branding
        secureNode?.getBranding(for: phoneNumber) { [weak self] result in
            guard let self = self else { return }
            
            switch result {
            case .success(let branding):
                if let brandName = branding.brandName {
                    update.localizedCallerName = brandName
                }
                
                // Load and set logo if available
                if let logoUrl = branding.logoUrl, let url = URL(string: logoUrl) {
                    self.loadCallImage(from: url) { image in
                        if let image = image {
                            update.hasVideo = false
                            // Note: CallKit image setting requires iOS 13+
                            // For older versions, this will be ignored
                        }
                        self.reportCall(uuid: uuid, update: update)
                    }
                } else {
                    self.reportCall(uuid: uuid, update: update)
                }
                
            case .failure:
                // No branding found - report call with default info
                self.reportCall(uuid: uuid, update: update)
            }
        }
    }
    
    private func loadCallImage(from url: URL, completion: @escaping (UIImage?) -> Void) {
        URLSession.shared.dataTask(with: url) { data, _, _ in
            guard let data = data, let image = UIImage(data: data) else {
                completion(nil)
                return
            }
            completion(image)
        }.resume()
    }
    
    private func reportCall(uuid: UUID, update: CXCallUpdate) {
        provider.reportNewIncomingCall(with: uuid, update: update) { error in
            if let error = error {
                print("Failed to report incoming call: \(error)")
            } else {
                // Record imprint for billing
                if let phoneNumber = self.activeCalls[uuid] {
                    self.recordImprint(phoneNumber: phoneNumber)
                }
            }
        }
    }
    
    private func recordImprint(phoneNumber: String) {
        // Record imprint via API for billing
        guard let apiUrl = secureNode?.config.apiURL else { return }
        let url = apiUrl.appendingPathComponent("mobile/branding/imprint")
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        
        let body: [String: Any] = [
            "phone_number_e164": phoneNumber,
            "displayed_at": ISO8601DateFormatter().string(from: Date())
        ]
        request.httpBody = try? JSONSerialization.data(withJSONObject: body)
        
        URLSession.shared.dataTask(with: request).resume()
    }
}

// MARK: - CXProviderDelegate

extension CallKitManager: CXProviderDelegate {
    func providerDidReset(_ provider: CXProvider) {
        activeCalls.removeAll()
    }
    
    func provider(_ provider: CXProvider, perform action: CXAnswerCallAction) {
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, perform action: CXEndCallAction) {
        if let uuid = action.callUUID as UUID? {
            activeCalls.removeValue(forKey: uuid)
        }
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, perform action: CXSetHeldCallAction) {
        action.fulfill()
    }
    
    func provider(_ provider: CXProvider, perform action: CXStartCallAction) {
        action.fulfill()
    }
}

