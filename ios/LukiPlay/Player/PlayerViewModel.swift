import AVFoundation
import Foundation

@MainActor
final class PlayerViewModel: ObservableObject {
    @Published private(set) var state: PlayerState = .idle
    @Published private(set) var currentConfig: StreamConfig?
    @Published var isPresented = false

    private(set) var savedPosition: CMTime = .zero

    func start(config: StreamConfig) {
        currentConfig = config
        state = .loading
        isPresented = true
    }

    func onPlaybackReady() {
        state = .playing
    }

    func onPlaybackBuffering() {
        if case .playing = state {
            return
        }
        state = .loading
    }

    func onPlaybackError(_ message: String) {
        state = .error(message)
    }

    func onRetry() {
        state = .loading
    }

    func savePosition(_ time: CMTime) {
        savedPosition = time
    }

    func stop() {
        state = .idle
        currentConfig = nil
        savedPosition = .zero
        isPresented = false
    }
}
