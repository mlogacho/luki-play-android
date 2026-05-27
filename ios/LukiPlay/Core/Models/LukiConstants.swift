import Foundation

enum LukiConstants {
    static let serverHost = "lukiplay.com"
    static let serverBase = "https://\(serverHost)"
    static let baseURL = URL(string: "\(serverBase)/home")!
    static let apiBaseURL = serverBase
    static let userAgentSuffix = "LukiPlay-iOS/1.0.0"

    enum SessionKeys {
        static let accessToken = "luki_access_token"
        static let refreshToken = "luki_refresh_token"
        static let userId = "luki_user_id"
        static let displayName = "luki_display_name"
    }
}
