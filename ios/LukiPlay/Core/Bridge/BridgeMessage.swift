import Foundation

enum BridgeMessage {
    case playStream(StreamConfig)
    case stopStream
    case loginSuccess(accessToken: String?, refreshToken: String?, userId: String?, displayName: String?)
    case logout
    case enterPip
    case getDeviceInfo(callbackId: String)
    case getStoredSession(callbackId: String)

    static func from(_ payload: Any) -> BridgeMessage? {
        let object: [String: Any]?

        if let string = payload as? String {
            let trimmed = string.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.lowercased() == "stop_stream" {
                return .stopStream
            }
            if trimmed.lowercased().hasPrefix("http://") || trimmed.lowercased().hasPrefix("https://") {
                return .playStream(StreamConfig(url: trimmed))
            }
        }

        if let dictionary = payload as? [String: Any] {
            object = dictionary
        } else if let string = payload as? String,
                  let data = string.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            object = json
        } else {
            object = nil
        }

        guard let object else { return nil }

        let type = (object["type"] as? String)?.lowercased() ?? ""

        if type.isEmpty, let url = object["url"] as? String, !url.isEmpty {
            let config = StreamConfig(
                url: url,
                title: object["title"] as? String ?? "",
                posterUrl: object["poster"] as? String,
                subtitleUri: object["subtitleUri"] as? String,
                subtitleMimeType: object["subtitleMimeType"] as? String ?? "text/vtt",
                drmToken: object["drmToken"] as? String
            )
            return .playStream(config)
        }

        switch type {
        case "play_stream":
            guard let url = object["url"] as? String, !url.isEmpty else { return nil }
            let config = StreamConfig(
                url: url,
                title: object["title"] as? String ?? "",
                posterUrl: object["poster"] as? String,
                subtitleUri: object["subtitleUri"] as? String,
                subtitleMimeType: object["subtitleMimeType"] as? String ?? "text/vtt",
                drmToken: object["drmToken"] as? String
            )
            return .playStream(config)

        case "stop_stream":
            return .stopStream

        case "login_success":
            return .loginSuccess(
                accessToken: object["accessToken"] as? String,
                refreshToken: object["refreshToken"] as? String,
                userId: object["userId"] as? String,
                displayName: object["displayName"] as? String
            )

        case "logout":
            return .logout

        case "enter_pip":
            return .enterPip

        case "get_device_info":
            return .getDeviceInfo(callbackId: object["callbackId"] as? String ?? "")

        case "get_stored_session":
            return .getStoredSession(callbackId: object["callbackId"] as? String ?? "")

        default:
            return nil
        }
    }
}
