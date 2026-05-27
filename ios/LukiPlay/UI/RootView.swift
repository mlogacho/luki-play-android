import SwiftUI

struct RootView: View {
    @StateObject private var playerViewModel = PlayerViewModel()
    private let playerManager = LukiPlayerManager()
    private let bridge: LukiBridgeController

    @State private var isWired = false

    init() {
        let sessionStore = SessionStore()
        let deviceInfoProvider = DeviceInfoProvider()
        self.bridge = LukiBridgeController(
            sessionStore: sessionStore,
            deviceInfoProvider: deviceInfoProvider
        )
    }

    var body: some View {
        WebViewContainer(bridge: bridge)
            .ignoresSafeArea()
            .fullScreenCover(isPresented: $playerViewModel.isPresented) {
                PlayerScreen(viewModel: playerViewModel, manager: playerManager)
            }
            .onAppear {
                guard !isWired else { return }
                isWired = true
                wireCallbacks()
            }
            .onDisappear {
                playerManager.releasePlayer()
            }
    }

    private func wireCallbacks() {
        bridge.onPlayStream = { config in
            Task { @MainActor in
                playerViewModel.start(config: config)
            }
        }

        bridge.onStopStream = {
            Task { @MainActor in
                let position = playerManager.saveAndPause()
                playerViewModel.savePosition(position)
                playerManager.stop()
                playerViewModel.stop()
            }
        }

        bridge.onEnterPip = {
            // Intentionally left empty for v1. AVPictureInPictureController
            // can be wired when custom controls are introduced.
        }

        playerManager.onReady = {
            Task { @MainActor in
                playerViewModel.onPlaybackReady()
            }
        }

        playerManager.onBuffering = {
            Task { @MainActor in
                playerViewModel.onPlaybackBuffering()
            }
        }

        playerManager.onEnded = {
            Task { @MainActor in
                playerViewModel.stop()
            }
        }

        playerManager.onError = { message in
            Task { @MainActor in
                playerViewModel.onPlaybackError(message)
            }
        }
    }
}
