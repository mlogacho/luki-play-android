import SwiftUI
import WebKit

struct WebViewContainer: UIViewRepresentable {
    let bridge: LukiBridgeController

    final class Coordinator: NSObject, WKNavigationDelegate {
        private let bridge: LukiBridgeController

        init(bridge: LukiBridgeController) {
            self.bridge = bridge
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            bridge.attach(to: webView)
            bridge.injectLegacyCaches()
        }
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(bridge: bridge)
    }

    func makeUIView(context: Context) -> WKWebView {
        let contentController = WKUserContentController()
        let script = WKUserScript(
            source: LukiBridgeController.jsShim(),
            injectionTime: .atDocumentStart,
            forMainFrameOnly: false
        )
        contentController.addUserScript(script)
        contentController.add(bridge, name: LukiBridgeController.channelName)

        let configuration = WKWebViewConfiguration()
        configuration.userContentController = contentController
        configuration.applicationNameForUserAgent = LukiConstants.userAgentSuffix

        let webView = WKWebView(frame: .zero, configuration: configuration)
        webView.navigationDelegate = context.coordinator
        bridge.attach(to: webView)
        bridge.injectLegacyCaches()
        webView.scrollView.contentInsetAdjustmentBehavior = .never
        webView.load(URLRequest(url: LukiConstants.baseURL))
        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        bridge.injectLegacyCaches()
    }
}
