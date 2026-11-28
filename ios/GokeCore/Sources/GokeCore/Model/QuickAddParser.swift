import Foundation
import GokeCoreModel

public enum QuickTokenKind: String, Sendable, Hashable, Codable {
    case date
    case time
    case priority
    case list
    case tag
    case course
    case remind
}

public struct QuickToken: Sendable, Hashable, Codable {
    public var text: String
    public var kind: QuickTokenKind

    public init(text: String, kind: QuickTokenKind) {
        self.text = text
        self.kind = kind
    }
}

/// Non-overlapping matched span of a recognized `QuickToken` in the raw input text.
public struct QuickTokenSpan: Sendable, Hashable {
    public var token: QuickToken
    public var range: Range<String.Index>
    /// UTF-16 code unit range (`NSRange(location: utf16Range.lowerBound, length: utf16Range.count)`).
    public var utf16Range: Range<Int>

    public init(token: QuickToken, range: Range<String.Index>, utf16Range: Range<Int>) {
        self.token = token
        self.range = range
        self.utf16Range = utf16Range
    }
}

/// Output of `QuickAddParser.rewriteQuickField`, containing rewritten text and end cursor offset in UTF-16 units.
public struct QuickFieldRewrite: Sendable, Hashable {
    public var text: String
    public var cursorUTF16: Int

    public init(text: String, cursorUTF16: Int? = nil) {
        self.text = text
        self.cursorUTF16 = cursorUTF16 ?? text.utf16.count
    }
}

public struct QuickAddResult: Sendable, Hashable {
    public var title: String
    public var due: LocalDateTime?
    public var dueHasTime: Bool
    public var priority: Int
    public var listName: String?
    public var tags: [String]
    /// `/课程名` — course linkage resolved by caller.
    public var courseName: String?
    /// Recognized raw substrings stripped from the title.
    public var tokens: [QuickToken]
    /// Normalized ascending reminder lead offsets in minutes; empty when no reminder syntax was present.
    public var remindMinutesList: [Int]
    /// Whether persistent reminder is enabled (only true when `remindMinutesList` is non-empty).
    public var remindPersistent: Bool
    /// `true` only when non-whitespace body text remains after stripping parsed tokens,
    /// preventing token-only input like `"明天 !高"` from being submitted as a task title.
    public var hasBodyText: Bool

    public init(
        title: String,
        due: LocalDateTime? = nil,
        dueHasTime: Bool = false,
        priority: Int = 0,
        listName: String? = nil,
        tags: [String] = [],
        courseName: String? = nil,
        tokens: [QuickToken] = [],
        remindMinutesList: [Int] = [],
        remindPersistent: Bool = false,
        hasBodyText: Bool? = nil
    ) {
        self.title = title
        self.due = due
        self.dueHasTime = dueHasTime
        self.priority = priority
        self.listName = listName
        self.tags = tags
        self.courseName = courseName
        self.tokens = tokens
        self.remindMinutesList = remindMinutesList
        self.remindPersistent = remindPersistent
        self.hasBodyText = hasBodyText ?? !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    /// Android-compatible predicate verifying that non-token body text exists in `rawInput`.
    public func hasBodyText(in rawInput: String) -> Bool {
        let trimmed = rawInput.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return false
        }
        return hasBodyText && !( !tokens.isEmpty && title == trimmed )
    }
}

public struct QuickBatchItem: Sendable, Hashable {
    public var result: QuickAddResult
    public var parentIndex: Int?

    public init(result: QuickAddResult, parentIndex: Int?) {
        self.result = result
        self.parentIndex = parentIndex
    }
}

/// Natural-language single-line and multi-line quick-add parser matching Android `QuickAdd.kt`.
public enum QuickAddParser {
    private static let cnDigits: [Character: Int] = [
        "零": 0, "〇": 0, "一": 1, "二": 2, "两": 2, "三": 3, "四": 4,
        "五": 5, "六": 6, "七": 7, "八": 8, "九": 9,
    ]

    private static let remindLB = "(^|[\\s,，。.;；:：、(\\[【（])"
    private static let remindBang = "([!！](?!(?:高|中|低|[123])(?![0-9])))?"
    private static let remindRB = "(?=$|[\\s,，。.;；:：、)\\]】）#@/]|[!！](?:高|中|低|[123])(?![0-9]))"
    private static let durNum = "(\\d{1,4}|[一二两三四五六七八九十]{1,3})"
    private static let durUnit = "(分钟|分|小时|时|天|min|m|h|d)"
    private static let periodPattern = "(清晨|早晨|早间|早上|上午|中午|午间|午后|下午|傍晚|晚间|晚上|夜间|夜里|半夜|深夜|凌晨)"
    private static let hourPart = "(\\d{1,2}|[一二两三四五六七八九十]{1,3})"
    private static let cnMin = "(?:[零〇][一二三四五六七八九]|[一二三四五]?十[一二三四五六七八九]?|[一二三四五六七八九](?=分))"
    private static let minPart = "(半|一刻|两刻|二刻|三刻|\\d{1,2}(?![A-Za-z])|\(cnMin))"

    private static let pmPeriods: Set<String> = ["下午", "午后", "傍晚", "晚上", "晚间", "夜间", "夜里"]
    private static let nightTwelvePeriods: Set<String> = ["晚上", "夜间", "夜里"]
    private static let earlyMorningPeriods: Set<String> = ["凌晨", "半夜", "深夜"]
    private static let lateNightPeriods: Set<String> = ["半夜", "深夜"]
    private static let noonPeriods: Set<String> = ["中午", "午间"]

    public static func cnNumber(_ s: String) -> Int? {
        if let n = Int(s) { return n }
        guard !s.isEmpty else { return nil }
        let cleaned = String(s.drop(while: { $0 == "零" || $0 == "〇" }))
        if cleaned.isEmpty { return 0 }
        if cleaned == "十" { return 10 }
        let chars = Array(cleaned)
        if chars.first == "十" {
            if chars.count == 2, let b = cnDigits[chars[1]] {
                return 10 + b
            }
            return nil
        }
        if let tenIdx = chars.firstIndex(of: "十"), tenIdx > 0 {
            guard tenIdx == 1, let a = cnDigits[chars[0]] else { return nil }
            if tenIdx + 1 == chars.count {
                return a * 10
            } else if tenIdx + 2 == chars.count, let b = cnDigits[chars[tenIdx + 1]] {
                return a * 10 + b
            } else {
                return nil
            }
        }
        return chars.count == 1 ? cnDigits[chars[0]] : nil
    }

    private static func parseDurationMinutes(numStr: String, unit: String) -> Int? {
        guard let n = cnNumber(numStr) else { return nil }
        let mins: Int
        switch unit.lowercased() {
        case "分钟", "分", "min", "m":
            mins = n
        case "小时", "时", "h":
            mins = n * 60
        case "天", "d":
            mins = n * 1440
        default:
            return nil
        }
        return (0...10080).contains(mins) ? mins : nil
    }

    private static func adjustHour(period: String, rawHour: Int, impliedPm: Bool) -> Int? {
        guard (0...24).contains(rawHour) else { return nil }
        var h = rawHour
        if pmPeriods.contains(period) {
            if nightTwelvePeriods.contains(period) && h == 12 {
                h = 0
            } else if (1...11).contains(h) {
                h += 12
            }
        } else if earlyMorningPeriods.contains(period) {
            if h == 12 {
                h = 0
            } else if lateNightPeriods.contains(period) && (10...11).contains(h) {
                h += 12
            }
        } else if noonPeriods.contains(period) {
            if (1...10).contains(h) {
                h += 12
            }
        } else if period.isEmpty && impliedPm && (1...11).contains(h) {
            h += 12
        }
        if h == 24 { h = 0 }
        return (0...23).contains(h) ? h : nil
    }

    private struct RegexCacheKey: Hashable {
        let pattern: String
        let optionsRawValue: UInt
    }

    private static let regexCacheLock = NSLock()
    private nonisolated(unsafe) static var regexCache: [RegexCacheKey: NSRegularExpression] = [:]

    private static func cachedRegex(_ pattern: String, options: NSRegularExpression.Options = []) -> NSRegularExpression? {
        let key = RegexCacheKey(pattern: pattern, optionsRawValue: options.rawValue)
        if let existing = regexCacheLock.withLock({ regexCache[key] }) {
            return existing
        }
        guard let compiled = try? NSRegularExpression(pattern: pattern, options: options) else { return nil }
        regexCacheLock.withLock {
            if regexCache.count >= 64 {
                regexCache.removeAll(keepingCapacity: true)
            }
            regexCache[key] = compiled
        }
        return compiled
    }

    private static func firstMatch(_ pattern: String, in text: String, options: NSRegularExpression.Options = [], fromUtf16Offset: Int = 0) -> NSTextCheckingResult? {
        guard let regex = cachedRegex(pattern, options: options) else { return nil }
        let nsText = text as NSString
        guard fromUtf16Offset <= nsText.length else { return nil }
        let searchRange = NSRange(location: fromUtf16Offset, length: nsText.length - fromUtf16Offset)
        return regex.firstMatch(in: text, options: [], range: searchRange)
    }

    private static func allMatches(_ pattern: String, in text: String, options: NSRegularExpression.Options = []) -> [NSTextCheckingResult] {
        guard let regex = cachedRegex(pattern, options: options) else { return [] }
        let nsText = text as NSString
        return regex.matches(in: text, options: [], range: NSRange(location: 0, length: nsText.length))
    }

    private static func group(_ match: NSTextCheckingResult, _ index: Int, in text: String) -> String {
        guard index < match.numberOfRanges else { return "" }
        let r = match.range(at: index)
        guard r.location != NSNotFound, let swiftRange = Range(r, in: text) else { return "" }
        return String(text[swiftRange])
    }

    public static func parse(
        _ input: String,
        now: LocalDateTime,
        config: QuickAddConfig = .default
    ) -> QuickAddResult {
        var text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        var priority = 0
        var listName: String? = nil
        var tags: [String] = []
        var tokens: [QuickToken] = []
        var courseName: String? = nil
        var remindOffsets: [Int] = []
        var remindPersistent = false

        func eat(_ value: String, kind: QuickTokenKind) {
            tokens.append(QuickToken(text: value.trimmingCharacters(in: .whitespacesAndNewlines), kind: kind))
            text = text.replacingOccurrences(of: value, with: " ")
        }

        func eatNSRange(_ nsRange: NSRange, kind: QuickTokenKind) {
            guard let swiftRange = Range(nsRange, in: text) else { return }
            let raw = String(text[swiftRange]).trimmingCharacters(in: .whitespacesAndNewlines)
            tokens.append(QuickToken(text: raw, kind: kind))
            text.replaceSubrange(swiftRange, with: " ")
        }

        // 1. Configurable reminder shorthands
        let kw = config.normalizedKeyword
        if !kw.isEmpty {
            let escKw = NSRegularExpression.escapedPattern(for: kw)
            let prefixPattern = "\(remindLB)(提前\\s*(?:(半)\\s*(?:小时|时)|\(durNum)\\s*\(durUnit))\\s*\(escKw)\(remindBang))\(remindRB)"
            let suffixPattern = "\(remindLB)(\(escKw)\\s*(?:(半)\\s*(?:小时|时)|\(durNum)\\s*\(durUnit))\(remindBang))\(remindRB)"
            let standalonePattern = "\(remindLB)(\(escKw)\(remindBang))\(remindRB)"

            func consumeOffsetPattern(_ pattern: String) {
                var offset = 0
                while offset <= (text as NSString).length {
                    guard let m = firstMatch(pattern, in: text, options: [.caseInsensitive], fromUtf16Offset: offset) else {
                        break
                    }
                    let halfStr = group(m, 3, in: text)
                    let mins: Int? = if halfStr == "半" {
                        30
                    } else {
                        parseDurationMinutes(numStr: group(m, 4, in: text), unit: group(m, 5, in: text))
                    }
                    let tokenRange = m.range(at: 2)
                    guard let validMins = mins else {
                        offset = tokenRange.location + tokenRange.length
                        continue
                    }
                    remindOffsets.append(validMins)
                    if !group(m, 6, in: text).isEmpty && config.bangPersistent {
                        remindPersistent = true
                    }
                    eatNSRange(tokenRange, kind: .remind)
                    offset = 0
                }
            }

            consumeOffsetPattern(prefixPattern)
            consumeOffsetPattern(suffixPattern)

            while let m = firstMatch(standalonePattern, in: text) {
                let hasBang = !group(m, 3, in: text).isEmpty
                if hasBang {
                    remindOffsets.append(contentsOf: config.effectivePersistentRemindMinutes)
                    if config.bangPersistent {
                        remindPersistent = true
                    }
                } else {
                    remindOffsets.append(contentsOf: config.effectiveRemindMinutes)
                }
                eatNSRange(m.range(at: 2), kind: .remind)
            }
        }

        // 2. Priority
        if let m = firstMatch("(?<![!！])[!！](高|中|低|[123])(?![0-9])", in: text) {
            switch group(m, 1, in: text) {
            case "高", "3": priority = 3
            case "中", "2": priority = 2
            default: priority = 1
            }
            eatNSRange(m.range, kind: .priority)
        }

        // 3. List (#list)
        if let m = firstMatch("#([^\\s#@!！，,。.？?]+)[，,。.？?]*", in: text) {
            listName = group(m, 1, in: text)
            eat(group(m, 0, in: text), kind: .list)
        }

        // 4. Tags (@tag)
        let tagMatches = allMatches("@([^\\s#@!！，,。.？?]+)[，,。.？?]*", in: text)
        let tagValues: [(full: String, name: String)] = tagMatches.map {
            (group($0, 0, in: text), group($0, 1, in: text))
        }
        for pair in tagValues {
            tags.append(pair.name)
            eat(pair.full, kind: .tag)
        }

        // 5. Course (/courseName) — start or after whitespace, avoiding 10/26 dates
        if let m = firstMatch("(?:^|\\s)/([^\\s#@!！/，,。.？?]+)", in: text) {
            courseName = group(m, 1, in: text)
            eat(group(m, 0, in: text), kind: .course)
        }

        var date: LocalDate? = nil
        var time: LocalTime? = nil
        var impliedPm = false
        let today = now.date

        // 6. Dates
        // 「周末」 before 「周X」
        if let m = firstMatch("(下下|下|这|本)?周末", in: text) {
            var d = today.startOfWeekMonday.addingDays(5) // Saturday
            if d < today { d = d.addingWeeks(1) }
            let mod = group(m, 1, in: text)
            let offsetWeeks = mod == "下" ? 1 : (mod == "下下" ? 2 : 0)
            date = d.addingWeeks(offsetWeeks)
            eat(group(m, 0, in: text), kind: .date)
        }

        if let m = firstMatch("(下下|下|这|本)?(周|星期|礼拜)([一二三四五六日天1-7])", in: text) {
            let dow: Int
            switch group(m, 3, in: text) {
            case "一", "1": dow = 1
            case "二", "2": dow = 2
            case "三", "3": dow = 3
            case "四", "4": dow = 4
            case "五", "5": dow = 5
            case "六", "6": dow = 6
            default: dow = 7
            }
            let mod = group(m, 1, in: text)
            let weekOffset = mod == "下" ? 1 : (mod == "下下" ? 2 : 0)
            var d = today.startOfWeekMonday.addingWeeks(weekOffset).addingDays(dow - 1)
            if weekOffset == 0 && d < today { d = d.addingWeeks(1) }
            date = d
            eat(group(m, 0, in: text), kind: .date)
        }

        if let m = firstMatch("(大后天|后天|明天|明早|明晚|今天|今晚|今早|今日|明日)", in: text) {
            let val = group(m, 0, in: text)
            switch val {
            case "大后天": date = today.addingDays(3)
            case "后天": date = today.addingDays(2)
            case "明天", "明日", "明早", "明晚": date = today.addingDays(1)
            default: date = today
            }
            if val.hasSuffix("晚") {
                time = LocalTime(hour: 20, minute: 0)
                impliedPm = true
            } else if val.hasSuffix("早") {
                time = LocalTime(hour: 8, minute: 0)
            }
            eat(val, kind: .date)
        }

        // Bare 「下周」 / 「下下周」
        if let m = firstMatch("(下下|下)周(?!\\s*[一二三四五六日天])", in: text) {
            let mod = group(m, 1, in: text)
            date = today.startOfWeekMonday.addingWeeks(mod == "下下" ? 2 : 1)
            eat(group(m, 0, in: text), kind: .date)
        }

        if let m = firstMatch("(\\d{1,3}|[一二两三四五六七八九十]{1,3})天后", in: text) {
            if let days = cnNumber(group(m, 1, in: text)) {
                date = today.addingDays(days)
            }
            eat(group(m, 0, in: text), kind: .date)
        }

        if let m = firstMatch("(这|本)?月底", in: text) {
            date = today.withDayOfMonth(today.lengthOfMonth)
            eat(group(m, 0, in: text), kind: .date)
        }

        if let m = firstMatch("(\\d{1,2})月(\\d{1,2})[日号]?", in: text) {
            if let mo = Int(group(m, 1, in: text)),
               let da = Int(group(m, 2, in: text)),
               var d = LocalDate(validatingYear: today.year, month: mo, day: da) {
                if d < today { d = d.addingYears(1) }
                date = d
            }
            eat(group(m, 0, in: text), kind: .date)
        }

        if let m = firstMatch("(\\d{4})-(\\d{1,2})-(\\d{1,2})", in: text) {
            if let y = Int(group(m, 1, in: text)),
               let mo = Int(group(m, 2, in: text)),
               let da = Int(group(m, 3, in: text)),
               let d = LocalDate(validatingYear: y, month: mo, day: da) {
                date = d
            }
            eat(group(m, 0, in: text), kind: .date)
        }

        // M/D or M-D (after ISO YYYY-MM-DD)
        if let m = firstMatch("(\\d{1,2})[/-](\\d{1,2})", in: text) {
            if let mo = Int(group(m, 1, in: text)),
               let da = Int(group(m, 2, in: text)),
               var d = LocalDate(validatingYear: today.year, month: mo, day: da) {
                if d < today { d = d.addingYears(1) }
                date = d
            }
            eat(group(m, 0, in: text), kind: .date)
        }

        // 「N号」
        if let m = firstMatch("(\\d{1,2})\\s*号", in: text) {
            if let da = Int(group(m, 1, in: text)),
               var d = LocalDate(validatingYear: today.year, month: today.month, day: da) {
                if d <= today { d = d.addingMonths(1) }
                date = d
                eat(group(m, 0, in: text), kind: .date)
            }
        }

        // 7. Times: first 「差X分Y点」, then Chinese/mixed/colon time
        var parsedExplicitTime = false
        let diffTimePattern = "\(periodPattern)?\\s*差\\s*(一刻|半|\\d{1,2}|[零〇一二两三四五六七八九十]{1,3})\\s*(?:分钟|分)?\\s*(?:到)?\\s*\(periodPattern)?\\s*(\\d{1,2}|[一二两三四五六七八九十]{1,3})\\s*(?:点钟?|时)"
        if let m = firstMatch(diffTimePattern, in: text) {
            let p1 = group(m, 1, in: text)
            let p3 = group(m, 3, in: text)
            let period = p1.isEmpty ? p3 : p1
            let subStr = group(m, 2, in: text)
            let subMin: Int? = switch subStr {
            case "一刻": 15
            case "半": 30
            default: cnNumber(subStr)
            }
            let targetHour = cnNumber(group(m, 4, in: text)).flatMap { adjustHour(period: period, rawHour: $0, impliedPm: impliedPm) }
            if let subMin, (1...59).contains(subMin), let targetHour {
                let total = ((targetHour * 60 - subMin) % 1440 + 1440) % 1440
                time = LocalTime(hour: total / 60, minute: total % 60)
                eatNSRange(m.range, kind: .time)
                parsedExplicitTime = true
            }
        }

        if !parsedExplicitTime {
            let stdTimePattern = "\(periodPattern)?\\s*\(hourPart)\\s*(?:(?:点|时)\\s*(?:过\\s*)?(?:\(minPart)\\s*(?:分钟|分)?|整|钟)?|(?:[:：])\\s*(\\d{1,2}(?![A-Za-z]))\\s*(?:分钟|分)?)"
            if let m = firstMatch(stdTimePattern, in: text) {
                let period = group(m, 1, in: text)
                let hour = cnNumber(group(m, 2, in: text)).flatMap { adjustHour(period: period, rawHour: $0, impliedPm: impliedPm) }
                let g3 = group(m, 3, in: text)
                let g4 = group(m, 4, in: text)
                let minStr = g3.isEmpty ? g4 : g3
                let mm: Int?
                switch minStr {
                case "半", "两刻", "二刻": mm = 30
                case "一刻": mm = 15
                case "三刻": mm = 45
                case "": mm = 0
                default:
                    if let n = cnNumber(minStr), (0...59).contains(n) {
                        mm = n
                    } else {
                        mm = nil
                    }
                }
                if let hour, let mm {
                    time = LocalTime(hour: hour, minute: mm)
                    eatNSRange(m.range, kind: .time)
                }
            }
        }

        let hasTime = time != nil
        var due: LocalDateTime? = nil
        if date != nil || time != nil {
            var d = date ?? today
            if date == nil, let t = time, LocalDateTime(date: d, time: t) < now {
                d = d.addingDays(1)
            }
            due = LocalDateTime(date: d, time: time ?? .midnight)
        }

        let finalRemind = Reminders.normalize(remindOffsets)
        let finalPersistent = remindPersistent && !finalRemind.isEmpty
        let collapsed = text
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let title = collapsed.isEmpty ? input.trimmingCharacters(in: .whitespacesAndNewlines) : collapsed

        return QuickAddResult(
            title: title,
            due: due,
            dueHasTime: hasTime,
            priority: priority,
            listName: listName,
            tags: tags,
            courseName: courseName,
            tokens: tokens,
            remindMinutesList: finalRemind,
            remindPersistent: finalPersistent,
            hasBodyText: !collapsed.isEmpty
        )
    }

    // MARK: - Token Spans & Single-Source Field Rewriting (Android QuickAddBar.kt Parity)

    private static let remindLeftDelims: Set<Character> = Set(",，。.;；:：、([【（")
    private static let remindRightDelims: Set<Character> = Set(",，。.;；:：、)]】）#@/!！")

    private static func isRemindBoundaryMatch(chars: [Character], start: Int, end: Int) -> Bool {
        let leftOk = start == 0
            || chars[start - 1].isWhitespace
            || remindLeftDelims.contains(chars[start - 1])
        let rightOk = end == chars.count
            || chars[end].isWhitespace
            || remindRightDelims.contains(chars[end])
        return leftOk && rightOk
    }

    /// Matches parsed `tokens` to non-overlapping character spans in `text`, enforcing remind-keyword
    /// delimiter boundaries so substrings like `"提醒"` inside `"不提醒"` are never falsely highlighted or stripped.
    public static func tokenRanges(
        in text: String,
        tokens: [QuickToken]
    ) -> [QuickTokenSpan] {
        guard !text.isEmpty, !tokens.isEmpty else { return [] }
        let chars = Array(text)
        var indices: [String.Index] = []
        indices.reserveCapacity(chars.count + 1)
        var idx = text.startIndex
        while idx < text.endIndex {
            indices.append(idx)
            idx = text.index(after: idx)
        }
        indices.append(text.endIndex)

        var used = Array(repeating: false, count: chars.count)
        var spans: [QuickTokenSpan] = []
        spans.reserveCapacity(tokens.count)

        for token in tokens {
            let tokenChars = Array(token.text)
            guard !tokenChars.isEmpty, tokenChars.count <= chars.count else { continue }
            let limit = chars.count - tokenChars.count
            var from = 0
            while from <= limit {
                if chars[from] == tokenChars[0] {
                    var matched = true
                    if tokenChars.count > 1 {
                        for offset in 1..<tokenChars.count where chars[from + offset] != tokenChars[offset] {
                            matched = false
                            break
                        }
                    }
                    if matched {
                        let end = from + tokenChars.count
                        let overlaps = (from..<end).contains { used[$0] }
                        let boundaryOk = token.kind != .remind
                            || isRemindBoundaryMatch(chars: chars, start: from, end: end)
                        if !overlaps && boundaryOk {
                            for k in from..<end {
                                used[k] = true
                            }
                            let range = indices[from]..<indices[end]
                            let utf16Start = text.utf16.distance(from: text.startIndex, to: range.lowerBound)
                            let utf16End = text.utf16.distance(from: text.startIndex, to: range.upperBound)
                            spans.append(QuickTokenSpan(token: token, range: range, utf16Range: utf16Start..<utf16End))
                            break
                        }
                    }
                }
                from += 1
            }
        }
        return spans
    }

    /// Parses `text` with `now` and `config` and returns non-overlapping `QuickTokenSpan`s.
    public static func tokenRanges(
        for text: String,
        now: LocalDateTime = .now(),
        config: QuickAddConfig = .default
    ) -> [QuickTokenSpan] {
        let parsed = parse(text, now: now, config: config)
        return tokenRanges(in: text, tokens: parsed.tokens)
    }

    /// Strips tokens matching `kinds` from `current` and optionally appends `add`, matching Android
    /// `rewriteQuickField`. Prefix prompts (`"#"`, `"@"`, `"/"`) omit trailing spaces and are idempotent.
    public static func rewriteQuickField(
        current: String,
        tokens: [QuickToken],
        kinds: Set<QuickTokenKind>,
        add: String?
    ) -> QuickFieldRewrite {
        let removeSpans = tokenRanges(in: current, tokens: tokens)
            .filter { kinds.contains($0.token.kind) }
            .sorted { $0.range.lowerBound < $1.range.lowerBound }

        var rebuilt = ""
        rebuilt.reserveCapacity(current.count)
        var cursor = current.startIndex
        for span in removeSpans {
            rebuilt.append(contentsOf: current[cursor..<span.range.lowerBound])
            rebuilt.append(" ")
            cursor = span.range.upperBound
        }
        rebuilt.append(contentsOf: current[cursor..<current.endIndex])

        let trimmed = rebuilt
            .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)

        let isPrefixPrompt = add == "#" || add == "@" || add == "/"
        let next: String
        if let add {
            if isPrefixPrompt && (trimmed == add || trimmed.hasSuffix(" \(add)")) {
                next = trimmed
            } else if trimmed.isEmpty {
                next = isPrefixPrompt ? add : "\(add) "
            } else if isPrefixPrompt {
                next = "\(trimmed) \(add)"
            } else {
                next = "\(trimmed) \(add) "
            }
        } else {
            next = trimmed
        }
        return QuickFieldRewrite(text: next, cursorUTF16: next.utf16.count)
    }

    /// Convenience overload of `rewriteQuickField` that parses `current` first.
    public static func rewriteQuickField(
        current: String,
        kinds: Set<QuickTokenKind>,
        add: String?,
        now: LocalDateTime = .now(),
        config: QuickAddConfig = .default
    ) -> QuickFieldRewrite {
        let parsed = parse(current, now: now, config: config)
        return rewriteQuickField(current: current, tokens: parsed.tokens, kinds: kinds, add: add)
    }

    /// Toggles a single `@tag` in `current`: removes the matching `@tag` span if already present,
    /// otherwise appends `@tag` via `rewriteQuickField`.
    public static func toggleTag(
        current: String,
        tokens: [QuickToken],
        tag: String
    ) -> QuickFieldRewrite {
        let cleanTag = tag.trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "^@+", with: "", options: .regularExpression)
        guard !cleanTag.isEmpty else {
            return QuickFieldRewrite(text: current)
        }
        let matchingSpans = tokenRanges(in: current, tokens: tokens)
            .filter { span in
                span.token.kind == .tag
                    && String(span.token.text.dropFirst())
                        .localizedCaseInsensitiveCompare(cleanTag) == .orderedSame
            }
            .sorted { $0.range.lowerBound < $1.range.lowerBound }

        if !matchingSpans.isEmpty {
            var rebuilt = ""
            rebuilt.reserveCapacity(current.count)
            var cursor = current.startIndex
            for span in matchingSpans {
                rebuilt.append(contentsOf: current[cursor..<span.range.lowerBound])
                rebuilt.append(" ")
                cursor = span.range.upperBound
            }
            rebuilt.append(contentsOf: current[cursor..<current.endIndex])
            let cleaned = rebuilt
                .replacingOccurrences(of: "\\s+", with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            return QuickFieldRewrite(text: cleaned, cursorUTF16: cleaned.utf16.count)
        }
        return rewriteQuickField(current: current, tokens: tokens, kinds: [], add: "@\(cleanTag)")
    }

    /// Multi-line paste parser: indented or `- `/`+`/`* ` lines attach as subtasks of the previous top-level item.
    public static func parseLines(
        _ input: String,
        now: LocalDateTime,
        config: QuickAddConfig = .default
    ) -> [QuickBatchItem] {
        var out: [QuickBatchItem] = []
        var lastTop: Int? = nil
        for raw in input.components(separatedBy: .newlines) {
            if raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { continue }
            let indented = raw.hasPrefix(" ") || raw.hasPrefix("\t") || raw.hasPrefix("\u{3000}")
            let dash = firstMatch("^\\s*[-*]\\s+", in: raw) != nil
            let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            let body = trimmed
                .replacingOccurrences(
                    of: "^([-*•·]|\\d{1,2}[.、)）])\\s*",
                    with: "",
                    options: .regularExpression
                )
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if body.isEmpty { continue }
            let parent = (indented || dash) ? lastTop : nil
            out.append(QuickBatchItem(result: parse(body, now: now, config: config), parentIndex: parent))
            if parent == nil {
                lastTop = out.count - 1
            }
        }
        return out
    }
}
