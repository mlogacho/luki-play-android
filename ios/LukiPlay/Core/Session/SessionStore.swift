import Foundation

struct StoredSession: Codable {
    let accessToken: String
    let refreshToken: String
    let userId: String
    let displayName: String

    var isValid: Bool {
        !refreshToken.isEmpty
    }
}

final class SessionStore {
    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    func save(accessToken: String?, refreshToken: String?, userId: String?, displayName: String?) {
        if let accessToken, !accessToken.isEmpty {
            defaults.set(accessToken, forKey: LukiConstants.SessionKeys.accessToken)
        }
        if let refreshToken, !refreshToken.isEmpty {
            defaults.set(refreshToken, forKey: LukiConstants.SessionKeys.refreshToken)
        }
        if let userId, !userId.isEmpty {
            defaults.set(userId, forKey: LukiConstants.SessionKeys.userId)
        }
        if let displayName, !displayName.isEmpty {
            defaults.set(displayName, forKey: LukiConstants.SessionKeys.displayName)
        }
    }

    func clear() {
        defaults.removeObject(forKey: LukiConstants.SessionKeys.accessToken)
        defaults.removeObject(forKey: LukiConstants.SessionKeys.refreshToken)
        defaults.removeObject(forKey: LukiConstants.SessionKeys.userId)
        defaults.removeObject(forKey: LukiConstants.SessionKeys.displayName)
    }

    func currentSession() -> StoredSession? {
        let accessToken = defaults.string(forKey: LukiConstants.SessionKeys.accessToken) ?? ""
        let refreshToken = defaults.string(forKey: LukiConstants.SessionKeys.refreshToken) ?? ""
        let userId = defaults.string(forKey: LukiConstants.SessionKeys.userId) ?? ""
        let displayName = defaults.string(forKey: LukiConstants.SessionKeys.displayName) ?? ""
        let session = StoredSession(
            accessToken: accessToken,
            refreshToken: refreshToken,
            userId: userId,
            displayName: displayName
        )
        return session.isValid ? session : nil
    }

    func currentSessionJSONString() -> String {
        guard let session = currentSession(),
              let data = try? JSONEncoder().encode(session),
              let string = String(data: data, encoding: .utf8)
        else {
            return "{}"
        }
        return string
    }
}
