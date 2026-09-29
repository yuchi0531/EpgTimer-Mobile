package com.starrow.epgtimer.ui

import com.starrow.epgtimer.data.edcb.EdcbException
import com.starrow.epgtimer.data.edcb.ErrCode
import com.starrow.epgtimer.data.model.ContentData
import com.starrow.epgtimer.data.model.EdcbDateTime
import com.starrow.epgtimer.util.EpgClock
import java.time.LocalDate
import java.time.LocalDateTime

private val WEEKDAY = arrayOf("月", "火", "水", "木", "金", "土", "日")

val REC_MODE_LABELS = listOf(
    "全サービス",
    "指定サービス",
    "全サービス(デコード処理なし)",
    "指定サービス(デコード処理なし)",
    "視聴",
)

private val REC_STATUS_LABELS = mapOf(
    1 to "録画終了",
    2 to "チューナーのオープンに失敗しました",
    3 to "録画中にキャンセルされた可能性があります",
    4 to "次の予約開始のためにキャンセルされました",
    5 to "録画時間に起動していなかった可能性があります",
    6 to "開始時間が変更されました",
    7 to "チューナー不足のため失敗しました",
    8 to "無効扱いでした",
    9 to "録画中に番組情報を確認できませんでした",
    10 to "指定時間番組情報が見つかりませんでした",
    11 to "録画終了（空き容量不足で別フォルダへの保存が発生）",
    12 to "録画開始処理に失敗しました",
    13 to "一部のみ録画が実行された可能性があります",
    14 to "指定チャンネルのデータがBonDriverから出力されなかった可能性があります",
    15 to "ファイル保存で致命的なエラーが発生した可能性があります",
)

private val RESERVE_STATUS_LABELS = linkedMapOf(
    0x01 to "イベントリレーで追加",
    0x02 to "6時間追従モード",
    0x04 to "最新EPGで変更済み(p/f)",
    0x08 to "最新EPGで変更済み",
    0x10 to "EPGなしで延長済み",
    0x20 to "終了未定状態",
)

val CONTENT_KIND_NAMES: Map<Int, String> = mapOf(
    0x0000 to "定時・総合",
    0x0001 to "天気",
    0x0002 to "特集・ドキュメント",
    0x0003 to "政治・国会",
    0x0004 to "経済・市況",
    0x0005 to "海外・国際",
    0x0006 to "解説",
    0x0007 to "討論・会談",
    0x0008 to "報道特番",
    0x0009 to "ローカル・地域",
    0x000A to "交通",
    0x000F to "その他",
    0x00FF to "ニュース／報道",
    0x0100 to "スポーツニュース",
    0x0101 to "野球",
    0x0102 to "サッカー",
    0x0103 to "ゴルフ",
    0x0104 to "その他の球技",
    0x0105 to "相撲・格闘技",
    0x0106 to "オリンピック・国際大会",
    0x0107 to "マラソン・陸上・水泳",
    0x0108 to "モータースポーツ",
    0x0109 to "マリン・ウィンタースポーツ",
    0x010A to "競馬・公営競技",
    0x010F to "その他",
    0x01FF to "スポーツ",
    0x0200 to "芸能・ワイドショー",
    0x0201 to "ファッション",
    0x0202 to "暮らし・住まい",
    0x0203 to "健康・医療",
    0x0204 to "ショッピング・通販",
    0x0205 to "グルメ・料理",
    0x0206 to "イベント",
    0x0207 to "番組紹介・お知らせ",
    0x020F to "その他",
    0x02FF to "情報／ワイドショー",
    0x0300 to "国内ドラマ",
    0x0301 to "海外ドラマ",
    0x0302 to "時代劇",
    0x030F to "その他",
    0x03FF to "ドラマ",
    0x0400 to "国内ロック・ポップス",
    0x0401 to "海外ロック・ポップス",
    0x0402 to "クラシック・オペラ",
    0x0403 to "ジャズ・フュージョン",
    0x0404 to "歌謡曲・演歌",
    0x0405 to "ライブ・コンサート",
    0x0406 to "ランキング・リクエスト",
    0x0407 to "カラオケ・のど自慢",
    0x0408 to "民謡・邦楽",
    0x0409 to "童謡・キッズ",
    0x040A to "民族音楽・ワールドミュージック",
    0x040F to "その他",
    0x04FF to "音楽",
    0x0500 to "クイズ",
    0x0501 to "ゲーム",
    0x0502 to "トークバラエティ",
    0x0503 to "お笑い・コメディ",
    0x0504 to "音楽バラエティ",
    0x0505 to "旅バラエティ",
    0x0506 to "料理バラエティ",
    0x050F to "その他",
    0x05FF to "バラエティ",
    0x0600 to "洋画",
    0x0601 to "邦画",
    0x0602 to "アニメ",
    0x060F to "その他",
    0x06FF to "映画",
    0x0700 to "国内アニメ",
    0x0701 to "海外アニメ",
    0x0702 to "特撮",
    0x070F to "その他",
    0x07FF to "アニメ／特撮",
    0x0800 to "社会・時事",
    0x0801 to "歴史・紀行",
    0x0802 to "自然・動物・環境",
    0x0803 to "宇宙・科学・医学",
    0x0804 to "カルチャー・伝統文化",
    0x0805 to "文学・文芸",
    0x0806 to "スポーツ",
    0x0807 to "ドキュメンタリー全般",
    0x0808 to "インタビュー・討論",
    0x080F to "その他",
    0x08FF to "ドキュメンタリー／教養",
    0x0900 to "現代劇・新劇",
    0x0901 to "ミュージカル",
    0x0902 to "ダンス・バレエ",
    0x0903 to "落語・演芸",
    0x0904 to "歌舞伎・古典",
    0x090F to "その他",
    0x09FF to "劇場／公演",
    0x0A00 to "旅・釣り・アウトドア",
    0x0A01 to "園芸・ペット・手芸",
    0x0A02 to "音楽・美術・工芸",
    0x0A03 to "囲碁・将棋",
    0x0A04 to "麻雀・パチンコ",
    0x0A05 to "車・オートバイ",
    0x0A06 to "コンピュータ・ＴＶゲーム",
    0x0A07 to "会話・語学",
    0x0A08 to "幼児・小学生",
    0x0A09 to "中学生・高校生",
    0x0A0A to "大学生・受験",
    0x0A0B to "生涯教育・資格",
    0x0A0C to "教育問題",
    0x0A0F to "その他",
    0x0AFF to "趣味／教育",
    0x0B00 to "高齢者",
    0x0B01 to "障害者",
    0x0B02 to "社会福祉",
    0x0B03 to "ボランティア",
    0x0B04 to "手話",
    0x0B05 to "文字（字幕）",
    0x0B06 to "音声解説",
    0x0B0F to "その他",
    0x0BFF to "福祉",
    0x0FFF to "その他",
    0x6000 to "中止の可能性あり",
    0x6001 to "延長の可能性あり",
    0x6002 to "中断の可能性あり",
    0x6003 to "別話数放送の可能性あり",
    0x6004 to "編成未定枠",
    0x6005 to "繰り上げの可能性あり",
    0x60FF to "編成情報",
    0x6100 to "中断ニュースあり",
    0x6101 to "臨時サービスあり",
    0x61FF to "特性情報",
    0x6200 to "3D映像あり",
    0x62FF to "3D映像",
    0x7000 to "テニス",
    0x7001 to "バスケットボール",
    0x7002 to "ラグビー",
    0x7003 to "アメリカンフットボール",
    0x7004 to "ボクシング",
    0x7005 to "プロレス",
    0x700F to "その他",
    0x70FF to "スポーツ(CS)",
    0x7100 to "アクション",
    0x7101 to "SF／ファンタジー",
    0x7102 to "コメディー",
    0x7103 to "サスペンス／ミステリー",
    0x7104 to "恋愛／ロマンス",
    0x7105 to "ホラー／スリラー",
    0x7106 to "ウエスタン",
    0x7107 to "ドラマ／社会派ドラマ",
    0x7108 to "アニメーション",
    0x7109 to "ドキュメンタリー",
    0x710A to "アドベンチャー／冒険",
    0x710B to "ミュージカル／音楽映画",
    0x710C to "ホームドラマ",
    0x710F to "その他",
    0x71FF to "洋画(CS)",
    0x7200 to "アクション",
    0x7201 to "SF／ファンタジー",
    0x7202 to "お笑い／コメディー",
    0x7203 to "サスペンス／ミステリー",
    0x7204 to "恋愛／ロマンス",
    0x7205 to "ホラー／スリラー",
    0x7206 to "青春／学園／アイドル",
    0x7207 to "任侠／時代劇",
    0x7208 to "アニメーション",
    0x7209 to "ドキュメンタリー",
    0x720A to "アドベンチャー／冒険",
    0x720B to "ミュージカル／音楽映画",
    0x720C to "ホームドラマ",
    0x720F to "その他",
    0x72FF to "邦画(CS)",
    0xFFFF to "なし",
)

val COMPONENT_KIND_NAMES: Map<Int, String> = mapOf(
    0x0101 to "480i(525i)、アスペクト比4:3",
    0x0102 to "480i(525i)、アスペクト比16:9 パンベクトルあり",
    0x0103 to "480i(525i)、アスペクト比16:9 パンベクトルなし",
    0x0104 to "480i(525i)、アスペクト比 > 16:9",
    0x0191 to "2160p、アスペクト比4:3",
    0x0192 to "2160p、アスペクト比16:9 パンベクトルあり",
    0x0193 to "2160p、アスペクト比16:9 パンベクトルなし",
    0x0194 to "2160p、アスペクト比 > 16:9",
    0x01A1 to "480p(525p)、アスペクト比4:3",
    0x01A2 to "480p(525p)、アスペクト比16:9 パンベクトルあり",
    0x01A3 to "480p(525p)、アスペクト比16:9 パンベクトルなし",
    0x01A4 to "480p(525p)、アスペクト比 > 16:9",
    0x01B1 to "1080i(1125i)、アスペクト比4:3",
    0x01B2 to "1080i(1125i)、アスペクト比16:9 パンベクトルあり",
    0x01B3 to "1080i(1125i)、アスペクト比16:9 パンベクトルなし",
    0x01B4 to "1080i(1125i)、アスペクト比 > 16:9",
    0x01C1 to "720p(750p)、アスペクト比4:3",
    0x01C2 to "720p(750p)、アスペクト比16:9 パンベクトルあり",
    0x01C3 to "720p(750p)、アスペクト比16:9 パンベクトルなし",
    0x01C4 to "720p(750p)、アスペクト比 > 16:9",
    0x01D1 to "240p アスペクト比4:3",
    0x01D2 to "240p アスペクト比16:9 パンベクトルあり",
    0x01D3 to "240p アスペクト比16:9 パンベクトルなし",
    0x01D4 to "240p アスペクト比 > 16:9",
    0x01E1 to "1080p(1125p)、アスペクト比4:3",
    0x01E2 to "1080p(1125p)、アスペクト比16:9 パンベクトルあり",
    0x01E3 to "1080p(1125p)、アスペクト比16:9 パンベクトルなし",
    0x01E4 to "1080p(1125p)、アスペクト比 > 16:9",
    0x0201 to "1/0モード（シングルモノ）",
    0x0202 to "1/0＋1/0モード（デュアルモノ）",
    0x0203 to "2/0モード（ステレオ）",
    0x0204 to "2/1モード",
    0x0205 to "3/0モード",
    0x0206 to "2/2モード",
    0x0207 to "3/1モード",
    0x0208 to "3/2モード",
    0x0209 to "3/2＋LFEモード（3/2.1モード）",
    0x020A to "3/3.1モード",
    0x020B to "2/0/0-2/0/2-0.1モード",
    0x020C to "5/2.1モード",
    0x020D to "3/2/2.1モード",
    0x020E to "2/0/0-3/0/2-0.1モード",
    0x020F to "0/2/0-3/0/2-0.1モード",
    0x0210 to "2/0/0-3/2/3-0.2モード",
    0x0211 to "3/3/3-5/2/3-3/0/0.2モード",
    0x0240 to "視覚障害者用音声解説",
    0x0241 to "聴覚障害者用音声",
    0x0501 to "H.264|MPEG-4 AVC、480i(525i)、アスペクト比4:3",
    0x0502 to "H.264|MPEG-4 AVC、480i(525i)、アスペクト比16:9 パンベクトルあり",
    0x0503 to "H.264|MPEG-4 AVC、480i(525i)、アスペクト比16:9 パンベクトルなし",
    0x0504 to "H.264|MPEG-4 AVC、480i(525i)、アスペクト比 > 16:9",
    0x0591 to "H.264|MPEG-4 AVC、2160p、アスペクト比4:3",
    0x0592 to "H.264|MPEG-4 AVC、2160p、アスペクト比16:9 パンベクトルあり",
    0x0593 to "H.264|MPEG-4 AVC、2160p、アスペクト比16:9 パンベクトルなし",
    0x0594 to "H.264|MPEG-4 AVC、2160p、アスペクト比 > 16:9",
    0x05A1 to "H.264|MPEG-4 AVC、480p(525p)、アスペクト比4:3",
    0x05A2 to "H.264|MPEG-4 AVC、480p(525p)、アスペクト比16:9 パンベクトルあり",
    0x05A3 to "H.264|MPEG-4 AVC、480p(525p)、アスペクト比16:9 パンベクトルなし",
    0x05A4 to "H.264|MPEG-4 AVC、480p(525p)、アスペクト比 > 16:9",
    0x05B1 to "H.264|MPEG-4 AVC、1080i(1125i)、アスペクト比4:3",
    0x05B2 to "H.264|MPEG-4 AVC、1080i(1125i)、アスペクト比16:9 パンベクトルあり",
    0x05B3 to "H.264|MPEG-4 AVC、1080i(1125i)、アスペクト比16:9 パンベクトルなし",
    0x05B4 to "H.264|MPEG-4 AVC、1080i(1125i)、アスペクト比 > 16:9",
    0x05C1 to "H.264|MPEG-4 AVC、720p(750p)、アスペクト比4:3",
    0x05C2 to "H.264|MPEG-4 AVC、720p(750p)、アスペクト比16:9 パンベクトルあり",
    0x05C3 to "H.264|MPEG-4 AVC、720p(750p)、アスペクト比16:9 パンベクトルなし",
    0x05C4 to "H.264|MPEG-4 AVC、720p(750p)、アスペクト比 > 16:9",
    0x05D1 to "H.264|MPEG-4 AVC、240p アスペクト比4:3",
    0x05D2 to "H.264|MPEG-4 AVC、240p アスペクト比16:9 パンベクトルあり",
    0x05D3 to "H.264|MPEG-4 AVC、240p アスペクト比16:9 パンベクトルなし",
    0x05D4 to "H.264|MPEG-4 AVC、240p アスペクト比 > 16:9",
    0x05E1 to "H.264|MPEG-4 AVC、1080p(1125p)、アスペクト比4:3",
    0x05E2 to "H.264|MPEG-4 AVC、1080p(1125p)、アスペクト比16:9 パンベクトルあり",
    0x05E3 to "H.264|MPEG-4 AVC、1080p(1125p)、アスペクト比16:9 パンベクトルなし",
    0x05E4 to "H.264|MPEG-4 AVC、1080p(1125p)、アスペクト比 > 16:9",
)

val LEVEL1_GENRES: List<Pair<Int, String>> = listOf(
    0x00 to "ニュース／報道",
    0x01 to "スポーツ",
    0x02 to "情報／ワイドショー",
    0x03 to "ドラマ",
    0x04 to "音楽",
    0x05 to "バラエティ",
    0x06 to "映画",
    0x07 to "アニメ／特撮",
    0x08 to "ドキュメンタリー／教養",
    0x09 to "劇場／公演",
    0x0A to "趣味／教育",
    0x0B to "福祉",
    0x0F to "その他",
)

fun contentDataForLevel1(level1: Int): ContentData =
    ContentData(nibbleLevel1 = level1, nibbleLevel2 = 0xFF, userNibble1 = 0, userNibble2 = 0)

fun genreNames(contents: List<ContentData>?): List<String> {
    if (contents.isNullOrEmpty()) return emptyList()
    return contents.mapNotNull { content ->
        CONTENT_KIND_NAMES[(content.nibbleLevel1 shl 8) or content.nibbleLevel2]
    }.distinct()
}

fun videoComponentText(streamContent: Int, componentType: Int, text: String): String {
    if (text.isNotBlank()) return text
    return COMPONENT_KIND_NAMES[(streamContent shl 8) or componentType] ?: "不明"
}

fun audioComponentTexts(components: List<com.starrow.epgtimer.data.model.AudioComponentData>?): List<String> {
    if (components.isNullOrEmpty()) return emptyList()
    return components.map { component ->
        if (component.text.isNotBlank()) {
            component.text
        } else {
            COMPONENT_KIND_NAMES[(component.streamContent shl 8) or component.componentType] ?: "不明"
        }
    }
}

fun freeCaText(freeCaFlag: Int): String = when (freeCaFlag) {
    0 -> "無料"
    1 -> "有料"
    else -> "不明"
}

fun recModeText(recSetting: com.starrow.epgtimer.data.model.RecSettingData): String {
    val base = REC_MODE_LABELS.getOrElse(recSetting.effectiveRecMode) { "不明(${recSetting.effectiveRecMode})" }
    return if (recSetting.isNoRec) "$base (無効)" else base
}

fun recStatusText(recStatus: Int, hasPath: Boolean): String = when (recStatus) {
    1 -> if (hasPath) "録画終了" else "終了"
    else -> REC_STATUS_LABELS[recStatus] ?: "不明($recStatus)"
}

fun overlapModeText(overlapMode: Int): String = when (overlapMode) {
    1 -> "かぶり(チューナー不足)"
    2 -> "チューナー不足で予約不可"
    else -> ""
}

fun reserveStatusText(reserveStatus: Int): String {
    if (reserveStatus == 0) return ""
    return RESERVE_STATUS_LABELS.filterKeys { reserveStatus and it != 0 }.values.joinToString(" / ")
}

fun errorText(throwable: Throwable): String {
    val message = throwable.message.orEmpty()
    return if (throwable is EdcbException) {
        if (message.isBlank()) ErrCode.name(throwable.code) else "${ErrCode.name(throwable.code)}: $message"
    } else if (message.isNotBlank()) {
        message
    } else {
        throwable.toString()
    }
}

fun weekdayText(date: LocalDate): String = WEEKDAY[date.dayOfWeek.value - 1]

fun dateLabel(date: LocalDate): String = "${date.monthValue}/${date.dayOfMonth}(${weekdayText(date)})"

fun timeLabel(time: LocalDateTime): String = "%02d:%02d".format(time.hour, time.minute)

fun dateTimeRangeLabel(start: LocalDateTime, end: LocalDateTime?): String {
    val endText = if (end == null) "?" else timeLabel(end)
    return "${dateLabel(start.toLocalDate())} ${timeLabel(start)}-$endText"
}

fun durationLabel(seconds: Int): String {
    val totalMinutes = seconds / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}時間${minutes}分" else "${minutes}分"
}

fun dayHeaderLabel(date: LocalDate): String {
    val today = EpgClock.now().toLocalDate()
    return when (date) {
        today -> "今日"
        today.plusDays(1) -> "明日"
        else -> dateLabel(date)
    }
}

fun dateTimeLabel(dateTime: EdcbDateTime): String {
    val value = dateTime.toLocalDateTime() ?: return "-"
    return "${dateLabel(value.toLocalDate())} ${timeLabel(value)}"
}
