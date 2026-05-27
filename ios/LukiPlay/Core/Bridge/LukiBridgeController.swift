import Foundation
import WebKit

final class LukiBridgeController: NSObject, WKScriptMessageHandler {
    static let channelName = "LukiNative"

    var onPlayStream: ((StreamConfig) -> Void)?
    var onStopStream: (() -> Void)?
    var onEnterPip: (() -> Void)?

    private weak var webView: WKWebView?
    private let sessionStore: SessionStore
    private let deviceInfoProvider: DeviceInfoProvider
    private var cachedDeviceInfoJSON = "{}"
    private var cachedStoredSessionJSON = "{}"

    init(sessionStore: SessionStore, deviceInfoProvider: DeviceInfoProvider) {
        self.sessionStore = sessionStore
        self.deviceInfoProvider = deviceInfoProvider
      self.cachedStoredSessionJSON = sessionStore.currentSessionJSONString()
      if let data = try? JSONSerialization.data(withJSONObject: deviceInfoProvider.payload()),
         let json = String(data: data, encoding: .utf8) {
        self.cachedDeviceInfoJSON = json
      }
    }

    func attach(to webView: WKWebView) {
        self.webView = webView
    }

    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        guard message.name == Self.channelName,
              let parsed = BridgeMessage.from(message.body)
        else {
            return
        }

        switch parsed {
        case .playStream(let config):
            onPlayStream?(config)

        case .stopStream:
            onStopStream?()

        case .enterPip:
            onEnterPip?()

        case .loginSuccess(let accessToken, let refreshToken, let userId, let displayName):
            sessionStore.save(
                accessToken: accessToken,
                refreshToken: refreshToken,
                userId: userId,
                displayName: displayName
            )
          cachedStoredSessionJSON = sessionStore.currentSessionJSONString()

        case .logout:
            sessionStore.clear()
          cachedStoredSessionJSON = "{}"

        case .getDeviceInfo(let callbackId):
          let payload = deviceInfoProvider.payload()
          if let data = try? JSONSerialization.data(withJSONObject: payload),
             let json = String(data: data, encoding: .utf8) {
            cachedDeviceInfoJSON = json
          }
          reply(callbackId: callbackId, payload: payload)

        case .getStoredSession(let callbackId):
            let response: [String: Any]
            if let session = sessionStore.currentSession(),
               let data = try? JSONEncoder().encode(session),
               let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                response = json
            } else {
                response = [:]
            }
          if let data = try? JSONSerialization.data(withJSONObject: response),
             let json = String(data: data, encoding: .utf8) {
            cachedStoredSessionJSON = json
          }
            reply(callbackId: callbackId, payload: response)
        }
    }

    static func jsShim() -> String {
        """
        (() => {
          if (window.LukiNative) return;
          const pending = new Map();
          const nativeState = {
            deviceInfoJSON: '{}',
            storedSessionJSON: '{}'
          };
          const post = (payload) => {
            window.webkit.messageHandlers.LukiNative.postMessage(payload);
          };

          const safeParse = (jsonString, fallback = {}) => {
            try { return JSON.parse(jsonString); } catch (_) { return fallback; }
          };

          window.LukiNative = {
            playStream: (raw) => {
              if (typeof raw === 'string' && /^https?:\/\//i.test(raw.trim())) {
                post({ type: 'play_stream', url: raw.trim() });
                return;
              }
              if (typeof raw === 'string') {
                try {
                  const parsed = JSON.parse(raw);
                  if (parsed && typeof parsed === 'object' && !parsed.type && parsed.url) {
                    post({ type: 'play_stream', ...parsed });
                    return;
                  }
                  post(parsed);
                  return;
                } catch (_) {}
              }
              if (raw && typeof raw === 'object' && !raw.type && raw.url) {
                post({ type: 'play_stream', ...raw });
                return;
              }
              post(raw);
            },
            stopStream: (raw) => {
              if (raw && typeof raw === 'object') {
                post({ type: 'stop_stream', ...raw });
                return;
              }
              post({ type: 'stop_stream' });
            },
            onLoginSuccess: (raw) => {
              const payload = typeof raw === 'string' ? JSON.parse(raw) : raw;
              post({ type: 'login_success', ...payload });
            },
            logout: () => post({ type: 'logout' }),
            enterPip: () => post({ type: 'enter_pip' }),
            getDeviceInfo: (legacyMode) => {
              if (legacyMode === true) {
                post({ type: 'get_device_info', callbackId: '' });
                return nativeState.deviceInfoJSON;
              }
              const callbackId = `cb_${Date.now()}_${Math.random().toString(36).slice(2)}`;
              return new Promise((resolve) => {
                pending.set(callbackId, resolve);
                post({ type: 'get_device_info', callbackId });
              });
            },
            getStoredSession: (legacyMode) => {
              if (legacyMode === true) {
                post({ type: 'get_stored_session', callbackId: '' });
                return nativeState.storedSessionJSON;
              }
              const callbackId = `cb_${Date.now()}_${Math.random().toString(36).slice(2)}`;
              return new Promise((resolve) => {
                pending.set(callbackId, resolve);
                post({ type: 'get_stored_session', callbackId });
              });
            },
            __resolve: (callbackId, payload) => {
              const resolve = pending.get(callbackId);
              if (!resolve) return;
              resolve(payload);
              pending.delete(callbackId);
            },
            __setLegacyCache: (kind, jsonString) => {
              if (kind === 'device') {
                nativeState.deviceInfoJSON = jsonString || '{}';
                return;
              }
              if (kind === 'session') {
                nativeState.storedSessionJSON = jsonString || '{}';
              }
            },
            __getLegacyCacheAsObject: (kind) => {
              if (kind === 'device') return safeParse(nativeState.deviceInfoJSON, {});
              if (kind === 'session') return safeParse(nativeState.storedSessionJSON, {});
              return {};
            }
          };
        })();
        """
    }

    private func reply(callbackId: String, payload: [String: Any]) {
        guard !callbackId.isEmpty,
              let data = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: data, encoding: .utf8)
        else {
            return
        }

        let escapedCallbackId = callbackId
            .replacingOccurrences(of: "\\", with: "\\\\")
            .replacingOccurrences(of: "'", with: "\\'")
        let js = "window.LukiNative && window.LukiNative.__resolve('\\(escapedCallbackId)', \\(json));"
        webView?.evaluateJavaScript(js)
    }

      func injectLegacyCaches() {
        let deviceEscaped = cachedDeviceInfoJSON
          .replacingOccurrences(of: "\\", with: "\\\\")
          .replacingOccurrences(of: "'", with: "\\'")
        let sessionEscaped = cachedStoredSessionJSON
          .replacingOccurrences(of: "\\", with: "\\\\")
          .replacingOccurrences(of: "'", with: "\\'")

        let js = """
        window.LukiNative && window.LukiNative.__setLegacyCache('device', '\(deviceEscaped)');
        window.LukiNative && window.LukiNative.__setLegacyCache('session', '\(sessionEscaped)');
        """
        webView?.evaluateJavaScript(js)
      }
}
