import AVFoundation
import Foundation

final class LukiPlayerManager {
    let player = AVPlayer()

    var onReady: (() -> Void)?
    var onBuffering: (() -> Void)?
    var onEnded: (() -> Void)?
    var onError: ((String) -> Void)?

    private var statusObserver: NSKeyValueObservation?
    private var timeControlObserver: NSKeyValueObservation?
    private var endObserver: NSObjectProtocol?

    deinit {
        releasePlayer()
    }

    func load(config: StreamConfig, startAt: CMTime = .zero) {
        guard let url = URL(string: config.url) else {
            onError?("Invalid stream URL")
            return
        }

        let item = AVPlayerItem(url: url)
        observe(item: item)

        player.replaceCurrentItem(with: item)
        if startAt.seconds > 0 {
            player.seek(to: startAt)
        }
        player.play()
    }

    func saveAndPause() -> CMTime {
        let position = player.currentTime()
        player.pause()
        return position
    }

    func resume() {
        player.play()
    }

    func stop() {
        player.pause()
        player.replaceCurrentItem(with: nil)
    }

    func releasePlayer() {
        stop()
        statusObserver?.invalidate()
        statusObserver = nil
        timeControlObserver?.invalidate()
        timeControlObserver = nil

        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
            self.endObserver = nil
        }
    }

    private func observe(item: AVPlayerItem) {
        statusObserver?.invalidate()
        timeControlObserver?.invalidate()

        statusObserver = item.observe(\.status, options: [.new]) { [weak self] observedItem, _ in
            guard let self else { return }
            switch observedItem.status {
            case .readyToPlay:
                self.onReady?()
            case .failed:
                self.onError?(observedItem.error?.localizedDescription ?? "Playback failed")
            default:
                break
            }
        }

        timeControlObserver = player.observe(\.timeControlStatus, options: [.new]) { [weak self] observedPlayer, _ in
            guard let self else { return }
            if observedPlayer.timeControlStatus == .waitingToPlayAtSpecifiedRate {
                self.onBuffering?()
            }
        }

        if let endObserver {
            NotificationCenter.default.removeObserver(endObserver)
        }
        endObserver = NotificationCenter.default.addObserver(
            forName: .AVPlayerItemDidPlayToEndTime,
            object: item,
            queue: .main
        ) { [weak self] _ in
            self?.onEnded?()
        }
    }
}
