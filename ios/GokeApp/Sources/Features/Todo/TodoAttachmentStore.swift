import Foundation
import GokeCore
import GokeShared
import ImageIO
import Photos
import PhotosUI
import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - Bounded Image & Aspect Ratio Memory Cache

private final class TodoAttachmentMemoryCache: @unchecked Sendable {
    static let shared = TodoAttachmentMemoryCache()

    private let imageCache: NSCache<NSString, UIImage>
    private let aspectLock = NSLock()
    private var aspectRatios: [String: CGFloat] = [:]
    private var memoryWarningObserver: NSObjectProtocol?

    private init() {
        let cache = NSCache<NSString, UIImage>()
        cache.totalCostLimit = 24 * 1024 * 1024
        cache.countLimit = 48
        self.imageCache = cache

        self.memoryWarningObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didReceiveMemoryWarningNotification,
            object: nil,
            queue: nil
        ) { [weak self] _ in
            self?.purgeImages()
        }
    }

    deinit {
        if let observer = memoryWarningObserver {
            NotificationCenter.default.removeObserver(observer)
        }
    }

    func image(forKey key: String) -> UIImage? {
        imageCache.object(forKey: key as NSString)
    }

    func insertImage(_ image: UIImage, forKey key: String) {
        let cost: Int
        if let cgImage = image.cgImage {
            cost = cgImage.bytesPerRow * cgImage.height
        } else {
            cost = Int(image.size.width * image.scale * image.size.height * image.scale * 4)
        }
        imageCache.setObject(image, forKey: key as NSString, cost: max(1, cost))
    }

    func purgeImages() {
        imageCache.removeAllObjects()
    }

    func aspectRatio(for file: String) -> CGFloat? {
        aspectLock.lock()
        defer { aspectLock.unlock() }
        return aspectRatios[file]
    }

    func setAspectRatio(_ ratio: CGFloat, for file: String) {
        aspectLock.lock()
        defer { aspectLock.unlock() }
        if aspectRatios.count >= 256, aspectRatios[file] == nil {
            aspectRatios.removeAll(keepingCapacity: true)
        }
        aspectRatios[file] = ratio
    }

    func removeAspectRatio(for file: String) {
        aspectLock.lock()
        defer { aspectLock.unlock() }
        aspectRatios.removeValue(forKey: file)
    }
}

// MARK: - Local Attachment Storage

/// Manages app-private local storage for todo image/file attachments (`attachments/<uuid>.<ext>`),
/// matching Android's `Attachments.kt` where records store only relative filenames.
enum TodoAttachmentStore {
    private static let directoryName = "attachments"

    private static let cachedDirectoryURL: URL = {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        let dir = base.appendingPathComponent("Goke", isDirectory: true)
            .appendingPathComponent(directoryName, isDirectory: true)
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        }
        return dir
    }()

    private static func imageCacheKey(for attachment: Attachment, maxPixelSize: CGFloat) -> String {
        "\(attachment.file)@\(max(64, Int(maxPixelSize)))"
    }

    static func directoryURL() -> URL {
        cachedDirectoryURL
    }

    static func fileURL(for attachment: Attachment) -> URL {
        cachedDirectoryURL.appendingPathComponent(attachment.file)
    }

    static func fileExists(for attachment: Attachment) -> Bool {
        FileManager.default.fileExists(atPath: fileURL(for: attachment).path)
    }

    /// O(1) memory-only aspect-ratio lookup (never hits disk; safe in SwiftUI `body`).
    static func cachedAspectRatio(for attachment: Attachment) -> CGFloat? {
        TodoAttachmentMemoryCache.shared.aspectRatio(for: attachment.file)
    }

    /// Reads natural pixel aspect ratio (`width / height`, EXIF-oriented) via `CGImageSource` without decoding bitmaps,
    /// caching the result in memory.
    static func imageAspectRatio(for attachment: Attachment) -> CGFloat? {
        if let cached = TodoAttachmentMemoryCache.shared.aspectRatio(for: attachment.file) {
            return cached
        }
        let url = fileURL(for: attachment)
        guard FileManager.default.fileExists(atPath: url.path),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let props = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [CFString: Any],
              let w = props[kCGImagePropertyPixelWidth] as? CGFloat,
              let h = props[kCGImagePropertyPixelHeight] as? CGFloat,
              w > 0, h > 0
        else { return nil }
        let orientation = (props[kCGImagePropertyOrientation] as? UInt32) ?? 1
        // EXIF orientations 5..8 rotate 90°/270° and swap width/height.
        let ratio = (5...8).contains(orientation) ? (h / w) : (w / h)
        TodoAttachmentMemoryCache.shared.setAspectRatio(ratio, for: attachment.file)
        return ratio
    }

    /// Resolves `imageAspectRatio` off the main thread when not already cached in memory.
    static func loadAspectRatio(for attachment: Attachment) async -> CGFloat? {
        if let cached = cachedAspectRatio(for: attachment) {
            return cached
        }
        return await Task.detached(priority: .utility) {
            imageAspectRatio(for: attachment)
        }.value
    }

    /// Writes raw image bytes into the private attachments directory and returns a new `Attachment` record.
    static func importImageData(
        _ data: Data,
        suggestedName: String? = nil,
        contentType: UTType? = nil
    ) -> Attachment? {
        guard !data.isEmpty else { return nil }
        let ext: String
        let mime: String
        if let contentType {
            ext = contentType.preferredFilenameExtension ?? "jpg"
            mime = contentType.preferredMIMEType ?? "image/jpeg"
        } else if data.starts(with: [0x89, 0x50, 0x4E, 0x47]) {
            ext = "png"
            mime = "image/png"
        } else {
            ext = "jpg"
            mime = "image/jpeg"
        }
        let fileName = "\(UUID().uuidString.lowercased()).\(ext)"
        let target = cachedDirectoryURL.appendingPathComponent(fileName)
        do {
            try data.write(to: target, options: .atomic)
            let displayName = suggestedName?.trimmingCharacters(in: .whitespacesAndNewlines)
            let finalName = (displayName?.isEmpty == false) ? displayName! : "图片.\(ext)"
            return Attachment(name: finalName, mime: mime, file: fileName)
        } catch {
            return nil
        }
    }

    /// Copies a user-selected file URL (e.g. from `.fileImporter`) into private attachment storage.
    static func importFileURL(_ url: URL) -> Attachment? {
        let accessed = url.startAccessingSecurityScopedResource()
        defer {
            if accessed {
                url.stopAccessingSecurityScopedResource()
            }
        }
        guard let data = try? Data(contentsOf: url), !data.isEmpty else { return nil }
        let ext = url.pathExtension.isEmpty ? "bin" : url.pathExtension.lowercased()
        let utType = UTType(filenameExtension: ext)
        let mime = utType?.preferredMIMEType ?? "application/octet-stream"
        let fileName = "\(UUID().uuidString.lowercased()).\(ext)"
        let target = cachedDirectoryURL.appendingPathComponent(fileName)
        do {
            try data.write(to: target, options: .atomic)
            let displayName = url.lastPathComponent.trimmingCharacters(in: .whitespacesAndNewlines)
            let finalName = displayName.isEmpty ? "附件.\(ext)" : displayName
            return Attachment(name: finalName, mime: mime, file: fileName)
        } catch {
            return nil
        }
    }

    /// Imports an image from the system clipboard (`UIPasteboard.general`), if present.
    @MainActor
    static func importImageFromPasteboard() -> Attachment? {
        guard let image = UIPasteboard.general.image else { return nil }
        if let pngData = image.pngData() {
            return importImageData(pngData, suggestedName: "剪贴板图片.png", contentType: .png)
        }
        if let jpegData = image.jpegData(compressionQuality: 0.9) {
            return importImageData(jpegData, suggestedName: "剪贴板图片.jpg", contentType: .jpeg)
        }
        return nil
    }

    static func delete(_ attachment: Attachment) {
        TodoAttachmentMemoryCache.shared.removeAspectRatio(for: attachment.file)
        let url = fileURL(for: attachment)
        try? FileManager.default.removeItem(at: url)
    }

    /// Safely saves an attachment image into the user's Photos library via `PHPhotoLibrary`.
    static func saveToPhotoLibrary(_ attachment: Attachment) async -> String {
        let url = fileURL(for: attachment)
        guard FileManager.default.fileExists(atPath: url.path) else {
            return "图片文件不存在"
        }
        let status = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        guard status == .authorized || status == .limited else {
            return "请在系统设置中允许写入相册"
        }
        do {
            try await PHPhotoLibrary.shared().performChanges {
                PHAssetChangeRequest.creationRequestForAssetFromImage(atFileURL: url)
            }
            return "已保存到相册"
        } catch {
            return "保存失败，请重试"
        }
    }

    /// O(1) memory-cache hit lookup for previously decoded downsampled attachment thumbnails/previews.
    static func cachedImage(for attachment: Attachment, maxPixelSize: CGFloat) -> UIImage? {
        let key = imageCacheKey(for: attachment, maxPixelSize: maxPixelSize)
        return TodoAttachmentMemoryCache.shared.image(forKey: key)
    }

    /// Downsamples an image file using `CGImageSource` so low-RAM devices avoid full-bitmap OOM,
    /// backed by a bounded `NSCache` that purges on memory warnings.
    static func loadDownsampledImage(for attachment: Attachment, maxPixelSize: CGFloat) -> UIImage? {
        let key = imageCacheKey(for: attachment, maxPixelSize: maxPixelSize)
        if let cached = TodoAttachmentMemoryCache.shared.image(forKey: key) {
            return cached
        }
        let url = fileURL(for: attachment)
        guard FileManager.default.fileExists(atPath: url.path) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceShouldCache: false,
        ]
        let decoded: UIImage?
        if let source = CGImageSourceCreateWithURL(url as CFURL, options as CFDictionary) {
            let thumbOptions: [CFString: Any] = [
                kCGImageSourceCreateThumbnailFromImageAlways: true,
                kCGImageSourceShouldCacheImmediately: true,
                kCGImageSourceCreateThumbnailWithTransform: true,
                kCGImageSourceThumbnailMaxPixelSize: max(64, Int(maxPixelSize)),
            ]
            if let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, thumbOptions as CFDictionary) {
                decoded = UIImage(cgImage: cgImage)
            } else {
                decoded = UIImage(contentsOfFile: url.path)
            }
        } else {
            decoded = UIImage(contentsOfFile: url.path)
        }
        if let decoded {
            TodoAttachmentMemoryCache.shared.insertImage(decoded, forKey: key)
        }
        return decoded
    }
}

// MARK: - TodoAttachmentEditorView

/// Inline todo attachment editor supporting aspect-ratio-aware 1-col images, crisp square 2/3-col
/// grids, `PhotosPicker` & `.fileImporter` (optional when bottom bar hosts 1-tap triggers),
/// clipboard paste, non-image file chips, deferred deletion, and upgraded full-screen image viewer.
struct TodoAttachmentEditorView: View {
    @Binding var attachments: [Attachment]
    var imageColumns: Binding<Int?>?
    var allowEditing: Bool
    var showPrimaryPickers: Bool

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance

    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var isImporting: Bool = false
    @State private var showFileImporter: Bool = false
    @State private var localColumns: Int?
    @State private var pasteFeedback: String?
    @State private var viewerSelection: ImageViewerSelection?
    @State private var measuredWidth: CGFloat = 340
    @State private var aspectRatios: [String: CGFloat] = [:]

    init(
        attachments: Binding<[Attachment]>,
        imageColumns: Binding<Int?>? = nil,
        allowEditing: Bool = true,
        showPrimaryPickers: Bool = true
    ) {
        self._attachments = attachments
        self.imageColumns = imageColumns
        self.allowEditing = allowEditing
        self.showPrimaryPickers = showPrimaryPickers
    }

    private struct ImageViewerSelection: Identifiable, Hashable {
        let id = UUID()
        let startIndex: Int
    }

    private var images: [Attachment] {
        attachments.filter(\.isImage)
    }

    private var files: [Attachment] {
        attachments.filter { !$0.isImage }
    }

    private var activeColumns: Int {
        let overrideCols = imageColumns?.wrappedValue ?? localColumns
        if let overrideCols {
            return min(max(overrideCols, 1), 3)
        }
        switch images.count {
        case ...1: return 1
        case 2...4: return 2
        default: return 3
        }
    }

    private func setColumns(_ next: Int) {
        if let imageColumns {
            imageColumns.wrappedValue = next
        } else {
            localColumns = next
        }
    }

    var body: some View {
        let hasClipboardImage = allowEditing && UIPasteboard.general.hasImages
        let showHeaderBar = allowEditing && (showPrimaryPickers || hasClipboardImage || images.count > 1)

        VStack(alignment: .leading, spacing: GokeSpacing.sm) {
            if showHeaderBar {
                HStack(spacing: GokeSpacing.sm) {
                    if showPrimaryPickers {
                        PhotosPicker(
                            selection: $pickerItems,
                            maxSelectionCount: 9,
                            matching: .images,
                            photoLibrary: .shared()
                        ) {
                            Label(
                                isImporting ? "正在导入…" : "图片",
                                systemImage: "photo.badge.plus"
                            )
                            .font(GokeTypography.labelLarge)
                            .foregroundStyle(theme.accent)
                            .padding(.horizontal, 10)
                            .frame(minHeight: 34)
                            .background(Capsule().fill(theme.accentContainer.opacity(0.55)))
                        }
                        .disabled(isImporting)

                        Button {
                            showFileImporter = true
                        } label: {
                            Label("文件", systemImage: "doc.badge.plus")
                                .font(GokeTypography.labelLarge)
                                .foregroundStyle(theme.accent)
                                .padding(.horizontal, 10)
                                .frame(minHeight: 34)
                                .background(Capsule().fill(theme.accentContainer.opacity(0.55)))
                        }
                        .buttonStyle(.mintCompact)
                        .disabled(isImporting)
                    }

                    if showPrimaryPickers || hasClipboardImage {
                        Button {
                            if let pasted = TodoAttachmentStore.importImageFromPasteboard() {
                                withAnimation(GokeMotion.snappy(policy: performance)) {
                                    attachments.append(pasted)
                                }
                                pasteFeedback = nil
                            } else {
                                pasteFeedback = "剪贴板无图片"
                            }
                        } label: {
                            Label("粘贴", systemImage: "doc.on.clipboard")
                                .font(GokeTypography.labelMedium.weight(.medium))
                                .foregroundStyle(theme.muted)
                                .padding(.horizontal, 10)
                                .frame(minHeight: 32)
                                .background(Capsule().fill(theme.surfaceHighest))
                        }
                        .buttonStyle(.mintCompact)
                    }

                    Spacer(minLength: 0)

                    if images.count > 1 {
                        Button {
                            let next = activeColumns >= 3 ? 1 : activeColumns + 1
                            withAnimation(GokeMotion.snappy(policy: performance)) {
                                setColumns(next)
                            }
                        } label: {
                            Label("\(activeColumns)列", systemImage: gridIconName(for: activeColumns))
                                .font(GokeTypography.labelMedium)
                                .monospacedDigit()
                                .foregroundStyle(theme.muted)
                                .padding(.horizontal, 10)
                                .frame(minHeight: 32)
                                .background(Capsule().fill(theme.surfaceHighest))
                        }
                        .buttonStyle(.mintCompact)
                        .accessibilityLabel("切换图片网格列数")
                    }
                }

                if let pasteFeedback {
                    Text(pasteFeedback)
                        .font(GokeTypography.labelSmall)
                        .foregroundStyle(theme.muted)
                }
            }

            if !images.isEmpty {
                let gap: CGFloat = activeColumns == 3 ? 6 : 8
                let cornerRadius: CGFloat = activeColumns == 3 ? GokeRadius.sm : GokeRadius.md
                let thumbMaxPixels: CGFloat = (performance.isDegraded || performance.isLowMemoryDevice) ? 420 : 760
                let tileWidth = max(64, (measuredWidth - gap * CGFloat(activeColumns - 1)) / CGFloat(activeColumns))
                let gridColumns = Array(
                    repeating: GridItem(.flexible(), spacing: gap),
                    count: activeColumns
                )

                LazyVGrid(columns: gridColumns, spacing: gap) {
                    ForEach(Array(images.enumerated()), id: \.element.file) { index, attachment in
                        let tileHeight = computedTileHeight(
                            for: attachment,
                            columns: activeColumns,
                            tileWidth: tileWidth
                        )
                        AttachmentThumbnailTile(
                            attachment: attachment,
                            height: tileHeight,
                            cornerRadius: cornerRadius,
                            maxPixelSize: thumbMaxPixels,
                            allowDelete: allowEditing,
                            onTap: {
                                viewerSelection = ImageViewerSelection(startIndex: index)
                            },
                            onDelete: {
                                removeAttachment(attachment)
                            }
                        )
                    }
                }
                .background(
                    GeometryReader { geo in
                        Color.clear
                            .onAppear {
                                if geo.size.width > 0 { measuredWidth = geo.size.width }
                            }
                            .onChange(of: geo.size.width) { _, newWidth in
                                if newWidth > 0 { measuredWidth = newWidth }
                            }
                    }
                )
            }

            if !files.isEmpty {
                TodoFlowLayout(horizontalSpacing: 8, verticalSpacing: 8) {
                    ForEach(files, id: \.file) { file in
                        HStack(spacing: 6) {
                            Image(systemName: "doc.fill")
                                .font(.caption)
                                .foregroundStyle(theme.accent)
                            Text(file.name)
                                .font(GokeTypography.labelMedium)
                                .foregroundStyle(theme.ink)
                                .lineLimit(1)
                                .frame(maxWidth: 180, alignment: .leading)
                            if TodoAttachmentStore.fileExists(for: file) {
                                ShareLink(item: TodoAttachmentStore.fileURL(for: file)) {
                                    Image(systemName: "square.and.arrow.up")
                                        .font(.caption.weight(.medium))
                                        .foregroundStyle(theme.accent)
                                        .frame(width: 28, height: 28)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.mintCompact)
                            }
                            if allowEditing {
                                Button(role: .destructive) {
                                    removeAttachment(file)
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .font(.caption)
                                        .foregroundStyle(theme.muted)
                                        .frame(width: 28, height: 28)
                                        .contentShape(Rectangle())
                                }
                                .buttonStyle(.mintCompact)
                            }
                        }
                        .padding(.leading, 10)
                        .padding(.trailing, 4)
                        .padding(.vertical, 4)
                        .background(
                            RoundedRectangle(cornerRadius: GokeRadius.sm, style: .continuous)
                                .fill(theme.surfaceHigh)
                        )
                    }
                }
            }
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.item],
            allowsMultipleSelection: true
        ) { result in
            guard case .success(let urls) = result, !urls.isEmpty else { return }
            isImporting = true
            Task {
                var imported: [Attachment] = []
                for url in urls {
                    if let att = TodoAttachmentStore.importFileURL(url) {
                        imported.append(att)
                    }
                }
                await MainActor.run {
                    if !imported.isEmpty {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            attachments.append(contentsOf: imported)
                        }
                    }
                    isImporting = false
                }
            }
        }
        .onChange(of: pickerItems) { _, newItems in
            guard !newItems.isEmpty else { return }
            let itemsToLoad = newItems
            pickerItems = []
            isImporting = true
            Task {
                var imported: [Attachment] = []
                for item in itemsToLoad {
                    if let data = try? await item.loadTransferable(type: Data.self) {
                        let contentType = item.supportedContentTypes.first(where: { $0.conforms(to: .image) })
                        if let attachment = TodoAttachmentStore.importImageData(data, contentType: contentType) {
                            imported.append(attachment)
                        }
                    }
                }
                await MainActor.run {
                    if !imported.isEmpty {
                        withAnimation(GokeMotion.snappy(policy: performance)) {
                            attachments.append(contentsOf: imported)
                        }
                    }
                    isImporting = false
                }
            }
        }
        .task(id: activeColumns == 1 ? images.map(\.file) : []) {
            guard activeColumns == 1 else { return }
            let missing = images.filter { att in
                aspectRatios[att.file] == nil && TodoAttachmentStore.cachedAspectRatio(for: att) == nil
            }
            guard !missing.isEmpty else { return }
            let resolved = await Task.detached(priority: .utility) {
                var out: [String: CGFloat] = [:]
                out.reserveCapacity(missing.count)
                for att in missing {
                    if let ar = TodoAttachmentStore.imageAspectRatio(for: att) {
                        out[att.file] = ar
                    }
                }
                return out
            }.value
            guard !Task.isCancelled, !resolved.isEmpty else { return }
            for (file, ratio) in resolved {
                aspectRatios[file] = ratio
            }
        }
        .fullScreenCover(item: $viewerSelection) { selection in
            TodoImageViewerSheet(
                images: images,
                initialIndex: selection.startIndex,
                onDelete: allowEditing ? { target in
                    removeAttachment(target)
                } : nil
            )
        }
    }

    private func computedTileHeight(for attachment: Attachment, columns: Int, tileWidth: CGFloat) -> CGFloat {
        if columns > 1 {
            return tileWidth // Crisp square tiles for 2-col and 3-col
        }
        // 1-col preserves natural aspect ratio clamped to [0.75, 1.75] and height [160, 360].
        // Memory-only lookup here; missing entries are computed asynchronously in `.task` off main thread.
        let rawAR = aspectRatios[attachment.file]
            ?? TodoAttachmentStore.cachedAspectRatio(for: attachment)
            ?? 1.4
        let clampedAR = min(max(rawAR, 0.75), 1.75)
        let idealHeight = tileWidth / clampedAR
        return min(max(idealHeight, 160), 360)
    }

    private func removeAttachment(_ target: Attachment) {
        // Defer physical disk deletion (keep file intact on disk so Cancel & Undo never leave broken references).
        withAnimation(GokeMotion.snappy(policy: performance)) {
            attachments.removeAll { $0.file == target.file }
        }
    }

    private func gridIconName(for cols: Int) -> String {
        switch cols {
        case 1: "rectangle.grid.1x2"
        case 2: "square.grid.2x2"
        default: "square.grid.3x3"
        }
    }
}

// MARK: - Thumbnail Tile

private struct AttachmentThumbnailTile: View {
    let attachment: Attachment
    let height: CGFloat
    let cornerRadius: CGFloat
    let maxPixelSize: CGFloat
    let allowDelete: Bool
    let onTap: () -> Void
    let onDelete: () -> Void

    @Environment(\.gokeTheme) private var theme
    @State private var image: UIImage?

    init(
        attachment: Attachment,
        height: CGFloat,
        cornerRadius: CGFloat,
        maxPixelSize: CGFloat,
        allowDelete: Bool,
        onTap: @escaping () -> Void,
        onDelete: @escaping () -> Void
    ) {
        self.attachment = attachment
        self.height = height
        self.cornerRadius = cornerRadius
        self.maxPixelSize = maxPixelSize
        self.allowDelete = allowDelete
        self.onTap = onTap
        self.onDelete = onDelete
        self._image = State(
            initialValue: TodoAttachmentStore.cachedImage(for: attachment, maxPixelSize: maxPixelSize)
        )
    }

    var body: some View {
        ZStack(alignment: .topTrailing) {
            Button(action: onTap) {
                Group {
                    if let image {
                        Image(uiImage: image)
                            .resizable()
                            .scaledToFill()
                    } else {
                        ZStack {
                            theme.surfaceHigh
                            Image(systemName: "photo")
                                .font(.title3)
                                .foregroundStyle(theme.muted)
                        }
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: height)
                .clipped()
                .clipShape(RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
            }
            .buttonStyle(.mintCompact)

            if allowDelete {
                Button(role: .destructive, action: onDelete) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 20))
                        .symbolRenderingMode(.palette)
                        .foregroundStyle(.white, Color.black.opacity(0.55))
                        .frame(width: 32, height: 32)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.mintCompact)
                .padding(2)
                .accessibilityLabel("移除图片附件")
            }
        }
        .task(id: "\(attachment.file)@\(Int(maxPixelSize))") {
            let fileAttachment = attachment
            let limit = maxPixelSize
            if let cached = TodoAttachmentStore.cachedImage(for: fileAttachment, maxPixelSize: limit) {
                image = cached
                return
            }
            let loaded = await Task.detached(priority: .userInitiated) {
                TodoAttachmentStore.loadDownsampledImage(for: fileAttachment, maxPixelSize: limit)
            }.value
            guard !Task.isCancelled else { return }
            image = loaded
        }
    }
}

// MARK: - Full-Screen Image Viewer (Tap Chrome, Swipe-Down Dismiss, Clamped Pan, Zoom Badge, Save to Photos)

struct TodoImageViewerSheet: View {
    let images: [Attachment]
    let initialIndex: Int
    let onDelete: ((Attachment) -> Void)?

    @Environment(\.gokeTheme) private var theme
    @Environment(\.gokePerformance) private var performance
    @Environment(\.dismiss) private var dismiss

    @State private var currentIndex: Int
    @State private var showChrome: Bool = true
    @State private var showDeleteConfirm: Bool = false
    @State private var currentScale: CGFloat = 1.0
    @State private var zoomResetToken: Int = 0
    @State private var dismissDragY: CGFloat = 0
    @State private var saveToastText: String?

    init(
        images: [Attachment],
        initialIndex: Int,
        onDelete: ((Attachment) -> Void)? = nil
    ) {
        self.images = images
        self.initialIndex = initialIndex
        self.onDelete = onDelete
        self._currentIndex = State(initialValue: max(0, min(initialIndex, max(0, images.count - 1))))
    }

    private var currentAttachment: Attachment? {
        guard images.indices.contains(currentIndex) else { return nil }
        return images[currentIndex]
    }

    private var backdropOpacity: Double {
        let progress = min(1.0, max(0.0, Double(abs(dismissDragY)) / 260.0))
        return max(0.2, 1.0 - progress * 0.8)
    }

    private var dragScaleFactor: CGFloat {
        let progress = min(1.0, max(0.0, abs(dismissDragY) / 360.0))
        return 1.0 - progress * 0.14
    }

    var body: some View {
        ZStack {
            Color.black
                .opacity(backdropOpacity)
                .ignoresSafeArea()

            if images.isEmpty {
                GokeEmptyState(systemImage: "photo", title: "暂无图片")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(theme.paper.ignoresSafeArea())
            } else {
                TabView(selection: $currentIndex) {
                    ForEach(Array(images.enumerated()), id: \.element.file) { index, item in
                        ZoomableAttachmentPage(
                            attachment: item,
                            maxPixelSize: (performance.isDegraded || performance.isLowMemoryDevice) ? 1600 : 2400,
                            resetToken: index == currentIndex ? zoomResetToken : 0,
                            onScaleChanged: { newScale in
                                if index == currentIndex {
                                    currentScale = newScale
                                }
                            },
                            onSingleTap: {
                                withAnimation(GokeMotion.snappy(policy: performance)) {
                                    showChrome.toggle()
                                }
                            },
                            onSwipeDismissChanged: { dy in
                                dismissDragY = dy
                            },
                            onSwipeDismissEnded: { dy, predictedDy in
                                if dy > 110 || predictedDy > 180 {
                                    dismiss()
                                } else {
                                    withAnimation(GokeMotion.snappy(policy: performance)) {
                                        dismissDragY = 0
                                    }
                                }
                            }
                        )
                        .tag(index)
                    }
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
                .scaleEffect(dragScaleFactor)
                .offset(y: dismissDragY)
                .ignoresSafeArea()
            }

            // Top & Bottom Gradient Chrome (toggled by single tap)
            if showChrome {
                VStack(spacing: 0) {
                    // Top Gradient Chrome
                    HStack(spacing: GokeSpacing.md) {
                        Button {
                            dismiss()
                        } label: {
                            Image(systemName: "xmark")
                                .font(.body.weight(.semibold))
                                .foregroundStyle(.white)
                                .frame(width: 36, height: 36)
                                .background(Color.black.opacity(0.5), in: Circle())
                                .frame(minWidth: 44, minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.mintCompact)
                        .accessibilityLabel("关闭预览")

                        Spacer(minLength: 0)

                        if images.count > 1 {
                            Text("\(currentIndex + 1) / \(images.count)")
                                .font(.subheadline.weight(.medium))
                                .monospacedDigit()
                                .foregroundStyle(.white)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 5)
                                .background(Color.black.opacity(0.5), in: Capsule())
                        }

                        Spacer(minLength: 0)

                        if currentScale > 1.05 {
                            Button {
                                zoomResetToken &+= 1
                            } label: {
                                Text(String(format: "%.1f×", currentScale))
                                    .font(.caption.weight(.semibold))
                                    .monospacedDigit()
                                    .foregroundStyle(.white)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Color.black.opacity(0.55), in: Capsule())
                                    .frame(minHeight: 44)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.mintCompact)
                            .accessibilityLabel("重置缩放至 1.0 倍")
                            .transition(.scale.combined(with: .opacity))
                        } else {
                            Color.clear.frame(width: 44, height: 44)
                        }
                    }
                    .padding(.horizontal, GokeSpacing.lg)
                    .padding(.top, GokeSpacing.md)
                    .padding(.bottom, GokeSpacing.lg)
                    .background(
                        LinearGradient(
                            colors: [Color.black.opacity(0.65), Color.clear],
                            startPoint: .top,
                            endPoint: .bottom
                        )
                        .ignoresSafeArea(edges: .top)
                    )

                    Spacer(minLength: 0)

                    // Bottom Gradient Action Bar: Save to Photos · Share · Delete
                    if let current = currentAttachment {
                        HStack(spacing: GokeSpacing.xl) {
                            if TodoAttachmentStore.fileExists(for: current) {
                                Button {
                                    Task {
                                        let msg = await TodoAttachmentStore.saveToPhotoLibrary(current)
                                        withAnimation(GokeMotion.snappy(policy: performance)) {
                                            saveToastText = msg
                                        }
                                        try? await Task.sleep(for: .seconds(2.2))
                                        withAnimation(GokeMotion.snappy(policy: performance)) {
                                            if saveToastText == msg {
                                                saveToastText = nil
                                            }
                                        }
                                    }
                                } label: {
                                    viewerActionItem(icon: "square.and.arrow.down", title: "保存")
                                }
                                .buttonStyle(.mintCompact)

                                ShareLink(item: TodoAttachmentStore.fileURL(for: current)) {
                                    viewerActionItem(icon: "square.and.arrow.up", title: "分享")
                                }
                                .buttonStyle(.mintCompact)
                            }

                            if onDelete != nil {
                                Button(role: .destructive) {
                                    showDeleteConfirm = true
                                } label: {
                                    viewerActionItem(icon: "trash", title: "删除", tint: Color(red: 1.0, green: 0.48, blue: 0.45))
                                }
                                .buttonStyle(.mintCompact)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.top, GokeSpacing.lg)
                        .padding(.bottom, GokeSpacing.md)
                        .background(
                            LinearGradient(
                                colors: [Color.clear, Color.black.opacity(0.72)],
                                startPoint: .top,
                                endPoint: .bottom
                            )
                            .ignoresSafeArea(edges: .bottom)
                        )
                    }
                }
                .transition(.opacity)
            }

            if let saveToastText {
                VStack {
                    Spacer()
                    Text(saveToastText)
                        .font(GokeTypography.labelLarge)
                        .foregroundStyle(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 10)
                        .background(Color.black.opacity(0.78), in: Capsule())
                        .padding(.bottom, 92)
                }
                .allowsHitTesting(false)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .onChange(of: currentIndex) { _, _ in
            currentScale = 1.0
            dismissDragY = 0
        }
        .confirmationDialog("删除这张图片？", isPresented: $showDeleteConfirm, titleVisibility: .visible) {
            Button("删除", role: .destructive) {
                if let target = currentAttachment {
                    onDelete?(target)
                    if images.count <= 1 {
                        dismiss()
                    } else if currentIndex >= images.count - 1 {
                        currentIndex = max(0, images.count - 2)
                    }
                }
            }
            Button("取消", role: .cancel) {}
        }
        .statusBarHidden(true)
    }

    private func viewerActionItem(icon: String, title: String, tint: Color = .white) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 19, weight: .medium))
            Text(title)
                .font(.caption.weight(.medium))
        }
        .foregroundStyle(tint)
        .frame(minWidth: 68, minHeight: 48)
        .contentShape(Rectangle())
    }
}

private struct ZoomableAttachmentPage: View {
    let attachment: Attachment
    let maxPixelSize: CGFloat
    let resetToken: Int
    let onScaleChanged: (CGFloat) -> Void
    let onSingleTap: () -> Void
    let onSwipeDismissChanged: (CGFloat) -> Void
    let onSwipeDismissEnded: (CGFloat, CGFloat) -> Void

    @Environment(\.gokePerformance) private var performance
    @State private var image: UIImage?
    @State private var scale: CGFloat = 1.0
    @State private var lastScale: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    init(
        attachment: Attachment,
        maxPixelSize: CGFloat,
        resetToken: Int,
        onScaleChanged: @escaping (CGFloat) -> Void,
        onSingleTap: @escaping () -> Void,
        onSwipeDismissChanged: @escaping (CGFloat) -> Void,
        onSwipeDismissEnded: @escaping (CGFloat, CGFloat) -> Void
    ) {
        self.attachment = attachment
        self.maxPixelSize = maxPixelSize
        self.resetToken = resetToken
        self.onScaleChanged = onScaleChanged
        self.onSingleTap = onSingleTap
        self.onSwipeDismissChanged = onSwipeDismissChanged
        self.onSwipeDismissEnded = onSwipeDismissEnded
        self._image = State(
            initialValue: TodoAttachmentStore.cachedImage(for: attachment, maxPixelSize: maxPixelSize)
        )
    }

    var body: some View {
        GeometryReader { geo in
            Group {
                if let image {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(width: geo.size.width, height: geo.size.height)
                        .scaleEffect(scale)
                        .offset(offset)
                        .contentShape(Rectangle())
                        .gesture(
                            MagnifyGesture()
                                .onChanged { value in
                                    let proposed = min(max(lastScale * value.magnification, 1.0), 4.0)
                                    scale = proposed
                                    offset = clampedOffset(offset, scale: proposed, size: geo.size)
                                    onScaleChanged(proposed)
                                }
                                .onEnded { _ in
                                    lastScale = scale
                                    if scale <= 1.02 {
                                        resetZoom()
                                    } else {
                                        offset = clampedOffset(offset, scale: scale, size: geo.size)
                                        lastOffset = offset
                                    }
                                }
                        )
                        .simultaneousGesture(
                            DragGesture(minimumDistance: 12)
                                .onChanged { value in
                                    if scale > 1.02 {
                                        let proposed = CGSize(
                                            width: lastOffset.width + value.translation.width,
                                            height: lastOffset.height + value.translation.height
                                        )
                                        offset = clampedOffset(proposed, scale: scale, size: geo.size)
                                    } else {
                                        let dy = value.translation.height
                                        let dx = value.translation.width
                                        if dy > 0 && abs(dy) > abs(dx) * 1.2 {
                                            onSwipeDismissChanged(dy)
                                        }
                                    }
                                }
                                .onEnded { value in
                                    if scale > 1.02 {
                                        lastOffset = offset
                                    } else {
                                        let dy = max(0, value.translation.height)
                                        let predicted = max(0, value.predictedEndTranslation.height)
                                        onSwipeDismissEnded(dy, predicted)
                                    }
                                }
                        )
                        .simultaneousGesture(
                            SpatialTapGesture(count: 2)
                                .onEnded { event in
                                    withAnimation(GokeMotion.snappy(policy: performance)) {
                                        if scale > 1.2 {
                                            scale = 1.0
                                            lastScale = 1.0
                                            offset = .zero
                                            lastOffset = .zero
                                            onScaleChanged(1.0)
                                        } else {
                                            let targetScale: CGFloat = 2.5
                                            let centerX = geo.size.width / 2
                                            let centerY = geo.size.height / 2
                                            let dx = (centerX - event.location.x) * (targetScale - 1)
                                            let dy = (centerY - event.location.y) * (targetScale - 1)
                                            scale = targetScale
                                            lastScale = targetScale
                                            offset = clampedOffset(CGSize(width: dx, height: dy), scale: targetScale, size: geo.size)
                                            lastOffset = offset
                                            onScaleChanged(targetScale)
                                        }
                                    }
                                }
                        )
                        .onTapGesture(count: 1) {
                            onSingleTap()
                        }
                } else {
                    ProgressView()
                        .tint(.white)
                        .frame(width: geo.size.width, height: geo.size.height)
                }
            }
        }
        .onChange(of: resetToken) { _, _ in
            if scale > 1.01 {
                resetZoom()
            }
        }
        .task(id: "\(attachment.file)@\(Int(maxPixelSize))") {
            let fileAttachment = attachment
            let limit = maxPixelSize
            if let cached = TodoAttachmentStore.cachedImage(for: fileAttachment, maxPixelSize: limit) {
                image = cached
                return
            }
            let loaded = await Task.detached(priority: .userInitiated) {
                TodoAttachmentStore.loadDownsampledImage(for: fileAttachment, maxPixelSize: limit)
            }.value
            guard !Task.isCancelled else { return }
            image = loaded
        }
    }

    private func clampedOffset(_ raw: CGSize, scale: CGFloat, size: CGSize) -> CGSize {
        let maxX = max(0, size.width * (scale - 1) / 2)
        let maxY = max(0, size.height * (scale - 1) / 2)
        return CGSize(
            width: min(max(raw.width, -maxX), maxX),
            height: min(max(raw.height, -maxY), maxY)
        )
    }

    private func resetZoom() {
        withAnimation(GokeMotion.snappy(policy: performance)) {
            scale = 1.0
            lastScale = 1.0
            offset = .zero
            lastOffset = .zero
            onScaleChanged(1.0)
        }
    }
}
