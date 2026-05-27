import Foundation

enum PlayerState: Equatable {
    case idle
    case loading
    case playing
    case error(String)
}
