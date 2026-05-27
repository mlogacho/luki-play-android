import AVKit
import Foundation
import UIKit

struct DeviceInfoProvider {
    func payload() -> [String: Any] {
        [
            "isTV": false,
            "label": UIDevice.current.model,
            "screenWidthDp": Int(UIScreen.main.bounds.width),
            "screenHeightDp": Int(UIScreen.main.bounds.height),
            "supportsPip": AVPictureInPictureController.isPictureInPictureSupported(),
            "deviceId": UIDevice.current.identifierForVendor?.uuidString ?? "ios-unknown",
            "platform": "ios",
            "apiBaseUrl": LukiConstants.apiBaseURL
        ]
    }
}
