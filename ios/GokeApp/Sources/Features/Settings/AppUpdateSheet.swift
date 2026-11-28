import Foundation
import GokeCore
import GokeShared
import SwiftUI

/// Sideload-friendly iOS update prompt supporting SideStore, AltStore, direct IPA download,
/// and GitHub Release inspection in the Mint Paper visual language.
struct AppUpdateSheet: View {
    let update: AppUpdateInfo
    let currentVersion: String
    let isAltStoreInstalled: Bool
    let onIgnore: () -> Void
    let onDismiss: () -> Void

    @Environment(\.gokeTheme) private var theme
    @Environment(\.openURL) private var openURL

    private var formattedSize: String? {
        guard update.ipaSizeBytes > 0 else { return nil }
        return ByteCountFormatter.string(fromByteCount: update.ipaSizeBytes, countStyle: .file)
    }

    private var notesText: String {
        let trimmed = update.notes.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "暂无更新说明。" : trimmed
    }

    var body: some View {
        NavigationStack {
            ScrollView(.vertical, showsIndicators: false) {
                VStack(alignment: .leading, spacing: GokeSpacing.lg) {
                    headerCard
                    releaseNotesCard
                    installActionsSection
                    footerActionsRow
                }
                .padding(.horizontal, GokeSpacing.lg)
                .padding(.top, GokeSpacing.sm)
                .padding(.bottom, GokeSpacing.xl)
            }
            .background(theme.paper.ignoresSafeArea())
            .navigationTitle("发现新版本")
            #if os(iOS)
            .navigationBarTitleDisplayMode(.inline)
            #endif
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭", action: onDismiss)
                        .foregroundStyle(theme.muted)
                }
            }
        }
    }

    private var headerCard: some View {
        HStack(spacing: 14) {
            GokeBrandMark(size: 48)

            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 8) {
                    Text("v\(update.version)")
                        .font(.title3.weight(.bold))
                        .monospacedDigit()
                        .foregroundStyle(theme.ink)

                    if update.isPrerelease {
                        GokeBadge("内测版", tone: .accent)
                    } else {
                        GokeBadge("正式版", tone: .accent)
                    }
                }

                HStack(spacing: 6) {
                    Text("当前 v\(currentVersion)")
                        .monospacedDigit()
                    if let formattedSize {
                        Text("·")
                        Text("IPA \(formattedSize)")
                            .monospacedDigit()
                    }
                }
                .font(GokeTypography.labelMedium)
                .foregroundStyle(theme.muted)
            }

            Spacer(minLength: 0)
        }
        .padding(GokeSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                .fill(theme.surface)
        )
    }

    private var releaseNotesCard: some View {
        VStack(alignment: .leading, spacing: GokeSpacing.sm) {
            Text("更新内容")
                .font(GokeTypography.labelLarge)
                .foregroundStyle(theme.muted)
                .padding(.horizontal, 4)

            Text(notesText)
                .font(GokeTypography.bodyMedium)
                .foregroundStyle(theme.ink)
                .textSelection(.enabled)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(GokeSpacing.md)
                .background(
                    RoundedRectangle(cornerRadius: GokeRadius.lg, style: .continuous)
                        .fill(theme.surface)
                )
        }
    }

    private var installActionsSection: some View {
        VStack(spacing: GokeSpacing.sm) {
            if let sideStoreURL = update.sideStoreInstallURL {
                Button {
                    openURL(sideStoreURL)
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "arrow.down.circle.fill")
                            .font(.system(size: 16, weight: .semibold))
                        Text("用 SideStore 安装")
                            .font(GokeTypography.bodyLarge.weight(.semibold))
                    }
                    .foregroundStyle(theme.onAccent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(
                        RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                            .fill(theme.accent)
                    )
                }
                .buttonStyle(.mintCompact)
            }

            if isAltStoreInstalled, let altStoreURL = update.altStoreInstallURL {
                secondaryActionButton(
                    title: "用 AltStore 安装",
                    systemImage: "square.and.arrow.down",
                    url: altStoreURL
                )
            }

            HStack(spacing: GokeSpacing.sm) {
                if let ipaURL = update.ipaURL {
                    secondaryActionButton(
                        title: "下载 IPA",
                        systemImage: "doc.zipper",
                        url: ipaURL
                    )
                }

                if let releaseURL = update.releasePageURL {
                    secondaryActionButton(
                        title: "查看 Release",
                        systemImage: "safari",
                        url: releaseURL
                    )
                }
            }

            if !isAltStoreInstalled, let altStoreURL = update.altStoreInstallURL {
                Menu {
                    Button {
                        openURL(altStoreURL)
                    } label: {
                        Label("用 AltStore 安装", systemImage: "square.and.arrow.down")
                    }
                } label: {
                    HStack(spacing: 6) {
                        Text("更多安装方式")
                            .font(GokeTypography.labelLarge)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 11, weight: .semibold))
                    }
                    .foregroundStyle(theme.muted)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
            }
        }
    }

    private func secondaryActionButton(
        title: String,
        systemImage: String,
        url: URL
    ) -> some View {
        Button {
            openURL(url)
        } label: {
            HStack(spacing: 6) {
                Image(systemName: systemImage)
                    .font(.system(size: 14, weight: .medium))
                Text(title)
                    .font(GokeTypography.bodyMedium.weight(.medium))
                    .lineLimit(1)
            }
            .foregroundStyle(theme.ink)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 12)
            .background(
                RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                    .fill(theme.surface)
                    .overlay(
                        RoundedRectangle(cornerRadius: GokeRadius.md, style: .continuous)
                            .stroke(theme.outlineVariant.opacity(0.6), lineWidth: 0.75)
                    )
            )
        }
        .buttonStyle(.mintCompact)
    }

    private var footerActionsRow: some View {
        HStack {
            Button(action: onIgnore) {
                Text("忽略此版本")
                    .font(GokeTypography.bodyMedium)
                    .foregroundStyle(theme.muted)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 12)
            }
            .buttonStyle(.mintCompact)

            Spacer()

            Button(action: onDismiss) {
                Text("稍后再说")
                    .font(GokeTypography.bodyMedium.weight(.medium))
                    .foregroundStyle(theme.accent)
                    .padding(.vertical, 8)
                    .padding(.horizontal, 12)
            }
            .buttonStyle(.mintCompact)
        }
    }
}
