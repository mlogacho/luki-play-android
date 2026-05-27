import Foundation

struct StreamConfig: Codable, Equatable {
    let url: String
    let title: String
    let posterUrl: String?
    let subtitleUri: String?
    let subtitleMimeType: String
    let drmToken: String?

    init(
        url: String,
        title: String = "",
        posterUrl: String? = nil,
        subtitleUri: String? = nil,
        subtitleMimeType: String = "text/vtt",
        drmToken: String? = nil
    ) {
        self.url = url
        self.title = title
        self.posterUrl = posterUrl
        self.subtitleUri = subtitleUri
        self.subtitleMimeType = subtitleMimeType
        self.drmToken = drmToken
    }
}
