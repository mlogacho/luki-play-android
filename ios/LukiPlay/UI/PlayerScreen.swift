import AVKit
import SwiftUI

struct PlayerScreen: View {
    @ObservedObject var viewModel: PlayerViewModel
    let manager: LukiPlayerManager

    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()

            VideoPlayer(player: manager.player)
                .ignoresSafeArea()

            if case .loading = viewModel.state {
                ProgressView("Loading stream...")
                    .padding(16)
                    .background(.ultraThinMaterial)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            if case .error(let message) = viewModel.state {
                VStack(spacing: 12) {
                    Text("Playback error")
                        .font(.headline)
                    Text(message)
                        .font(.subheadline)
                        .multilineTextAlignment(.center)
                    Button("Retry") {
                        viewModel.onRetry()
                        if let config = viewModel.currentConfig {
                            manager.load(config: config, startAt: viewModel.savedPosition)
                        }
                    }
                    .buttonStyle(.borderedProminent)
                }
                .padding(20)
                .background(.ultraThinMaterial)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .padding(24)
            }

            VStack {
                HStack {
                    Spacer()
                    Button("Close") {
                        let position = manager.saveAndPause()
                        viewModel.savePosition(position)
                        manager.stop()
                        viewModel.stop()
                    }
                    .buttonStyle(.bordered)
                    .padding()
                }
                Spacer()
            }
        }
        .onAppear {
            guard let config = viewModel.currentConfig else { return }
            manager.load(config: config, startAt: viewModel.savedPosition)
        }
        .onDisappear {
            let position = manager.saveAndPause()
            viewModel.savePosition(position)
        }
    }
}
