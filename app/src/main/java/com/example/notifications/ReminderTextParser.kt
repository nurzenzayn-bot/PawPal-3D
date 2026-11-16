package com.example.notifications

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale

/** Pure, on-device Indonesian reminder extraction. No chat content leaves the device. */
internal object ReminderTextParser {
    data class ParsedReminder(val title: String, val at: Long)

    private const val DEFAULT_HOUR = 8
    private val eventWords = listOf(
        "acara", "nikah", "nikahan", "menikah", "pernikahan", "kondangan", "ulang tahun", "ultah", "rapat", "meeting", "pertemuan", "jadwal",
        "ujian", "tes", "interview", "wawancara", "janji", "kontrol", "dokter", "wisuda", "pesta",
        "deadline", "presentasi", "kumpul", "berangkat", "perjalanan", "lomba", "pertandingan",
        "konser", "kerja", "sekolah", "kuliah", "les", "kursus", "arisan", "ibadah", "jemput",
        "antar", "ambil", "bertemu", "ketemu", "penerbangan", "traveling", "liburan", "servis", "periksa", "appointment"
    )
    private val monthNumbers = mapOf(
        "januari" to Calendar.JANUARY, "jan" to Calendar.JANUARY,
        "februari" to Calendar.FEBRUARY, "feb" to Calendar.FEBRUARY,
        "maret" to Calendar.MARCH, "mar" to Calendar.MARCH,
        "april" to Calendar.APRIL, "apr" to Calendar.APRIL,
        "mei" to Calendar.MAY,
        "juni" to Calendar.JUNE, "jun" to Calendar.JUNE,
        "juli" to Calendar.JULY, "jul" to Calendar.JULY,
        "agustus" to Calendar.AUGUST, "agu" to Calendar.AUGUST,
        "september" to Calendar.SEPTEMBER, "sep" to Calendar.SEPTEMBER,
        "oktober" to Calendar.OCTOBER, "okt" to Calendar.OCTOBER,
        "november" to Calendar.NOVEMBER, "nov" to Calendar.NOVEMBER,
        "desember" to Calendar.DECEMBER, "des" to Calendar.DECEMBER
    )
    private val weekdayNumbers = mapOf(
        "minggu" to Calendar.SUNDAY,
        "senin" to Calendar.MONDAY,
        "selasa" to Calendar.TUESDAY,
        "rabu" to Calendar.WEDNESDAY,
        "kamis" to Calendar.THURSDAY,
        "jumat" to Calendar.FRIDAY,
        "sabtu" to Calendar.SATURDAY
    )

    fun parse(message: String, nowMillis: Long = System.currentTimeMillis()): ParsedReminder? {
        if (message.isBlank()) return null
        val normalized = normalize(message)
        if (isHypotheticalOrCancelled(normalized)) return null
        val hasExplicitClockTime = Regex("\\b(?:jam|pukul)\\s*\\d{1,2}(?:[:.]\\d{2})?").containsMatchIn(normalized)
        val petCareIntent = isPetCareIntent(normalized) && hasExplicitClockTime
        if (!petCareIntent && eventWords.none { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(normalized) }) return null

        // Pet-care reminders need an explicit clock time; when no date is stated,
        // schedule today or tomorrow, matching the v5 care-reminder behavior.

        val base = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val target = resolveDate(normalized, base) ?: if (petCareIntent && !hasDateReference(normalized)) {
            (base.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
        } else return null
        val resolvedTime = resolveTime(normalized) ?: return null
        val (hour, minute) = resolvedTime
        target.set(Calendar.HOUR_OF_DAY, hour)
        target.set(Calendar.MINUTE, minute)
        target.set(Calendar.SECOND, 0)
        target.set(Calendar.MILLISECOND, 0)

        if (target.timeInMillis <= nowMillis) {
            // A same-day reminder whose requested time has just passed should still be useful.
            // Explicit weekdays/dates that are in the past are moved to their next occurrence.
            when {
                hasRelativeToday(normalized) -> target.timeInMillis = nowMillis + 60_000L
                hasWeekday(normalized) -> target.add(Calendar.DAY_OF_YEAR, 7)
                petCareIntent && !hasDateReference(normalized) -> target.add(Calendar.DAY_OF_YEAR, 1)
                else -> return null
            }
        }

        val title = if (petCareIntent) extractPetCareTitle(normalized) else extractTitle(normalized)
        if (title.isBlank()) return null
        return ParsedReminder(title, target.timeInMillis)
    }

    private fun isPetCareIntent(text: String): Boolean =
        Regex("\\b(makan|pakan|mandi|bersih|main|bermain|minum|tidur|istirahat)\\b").containsMatchIn(text)

    private fun extractPetCareTitle(text: String): String = when {
        Regex("\\b(makan|pakan)\\b").containsMatchIn(text) -> "Waktunya memberi makan"
        Regex("\\b(mandi|bersih)\\b").containsMatchIn(text) -> "Waktunya bersih-bersih"
        Regex("\\b(main|bermain)\\b").containsMatchIn(text) -> "Waktunya bermain"
        Regex("\\bminum\\b").containsMatchIn(text) -> "Cek air minum"
        else -> "Waktunya istirahat"
    }

    private fun hasDateReference(text: String): Boolean =
        Regex("\\b(hari ini|nanti hari ini|besok|esok|lusa|dua hari lagi|minggu depan|senin|selasa|rabu|kamis|jumat|sabtu|minggu|tanggal)\\b")
            .containsMatchIn(text) ||
            Regex("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b").containsMatchIn(text) ||
            Regex("\\b\\d{1,2}\\s+(januari|jan|februari|feb|maret|mar|april|apr|mei|juni|jun|juli|jul|agustus|agu|september|sep|oktober|okt|november|nov|desember|des)\\b")
                .containsMatchIn(text)

    private fun normalize(value: String): String = Normalizer.normalize(
        value.lowercase(Locale.forLanguageTag("id-ID")), Normalizer.Form.NFD
    ).replace(Regex("\\p{Mn}+"), "").replace(Regex("\\s+"), " ").trim()

    private fun isHypotheticalOrCancelled(text: String): Boolean {
        val hypothetical = Regex("\\b(kalau|jika|andaikan|andaikata|seandainya|misalnya|misal)\\b")
        if (hypothetical.containsMatchIn(text)) return true
        if (Regex("\\b(tidak jadi|gak jadi|ga jadi|nggak jadi|enggak jadi|batal|dibatalkan)\\b").containsMatchIn(text)) return true
        if (Regex("\\b(tidak tahu|ga tahu|gak tahu|nggak tahu)\\b").containsMatchIn(text)) return true
        return Regex("\\b(tidak|gak|ga|nggak|enggak|belum)\\b.{0,36}\\b(ada acara|acara|nikah|rapat|ujian|meeting|janji|wisuda|konser|pergi|berangkat)\\b").containsMatchIn(text)
    }

    private fun resolveDate(text: String, base: Calendar): Calendar? {
        val result = (base.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        when {
            Regex("\\b(minggu depan)\\b").containsMatchIn(text) &&
                !Regex("\\bhari minggu depan\\b").containsMatchIn(text) -> {
                result.add(Calendar.DAY_OF_YEAR, 7)
                return result
            }
            Regex("\\b(lusa|dua hari lagi)\\b").containsMatchIn(text) -> {
                result.add(Calendar.DAY_OF_YEAR, 2)
                return result
            }
            Regex("\\b(besok|esok)\\b").containsMatchIn(text) -> {
                result.add(Calendar.DAY_OF_YEAR, 1)
                return result
            }
            Regex("\\b(hari ini|nanti hari ini|nanti pagi|nanti siang|nanti sore|nanti malam|pagi ini|siang ini|sore ini|malam ini)\\b").containsMatchIn(text) -> return result
        }

        val weekdayMatch = Regex("\\b(senin|selasa|rabu|kamis|jumat|sabtu|minggu)(?:\\s+(ini|depan))?\\b").find(text)
        if (weekdayMatch != null) {
            val day = weekdayNumbers[weekdayMatch.groupValues[1]] ?: return null
            var delta = (day - base.get(Calendar.DAY_OF_WEEK) + 7) % 7
            if (weekdayMatch.groupValues[2] == "depan" && delta == 0) delta = 7
            result.add(Calendar.DAY_OF_YEAR, delta)
            return result
        }

        val slashDate = Regex("\\b(\\d{1,2})[/-](\\d{1,2})(?:[/-](\\d{2,4}))?\\b").find(text)
        if (slashDate != null) {
            val day = slashDate.groupValues[1].toIntOrNull() ?: return null
            val month = (slashDate.groupValues[2].toIntOrNull() ?: return null) - 1
            val yearText = slashDate.groupValues[3]
            val year = yearText.toIntOrNull()?.let { if (it < 100) 2000 + it else it }
                ?: base.get(Calendar.YEAR)
            return buildValidatedDate(day, month, year)?.also { date ->
                if (yearText.isBlank() && date.timeInMillis < base.startOfDayMillis()) date.add(Calendar.YEAR, 1)
            }
        }

        val monthDate = Regex("\\b(?:tanggal\\s+)?(\\d{1,2})\\s+(januari|jan|februari|feb|maret|mar|april|apr|mei|juni|jun|juli|jul|agustus|agu|september|sep|oktober|okt|november|nov|desember|des)(?:\\s+(\\d{4}))?\\b").find(text)
        if (monthDate != null) {
            val day = monthDate.groupValues[1].toIntOrNull() ?: return null
            val month = monthNumbers[monthDate.groupValues[2]] ?: return null
            var year = monthDate.groupValues[3].toIntOrNull() ?: base.get(Calendar.YEAR)
            var date = buildValidatedDate(day, month, year) ?: return null
            if (monthDate.groupValues[3].isBlank() && date.timeInMillis < base.startOfDayMillis()) {
                year += 1
                date = buildValidatedDate(day, month, year) ?: return null
            }
            return date
        }

        val namedMonthFirst = Regex("\\b(januari|jan|februari|feb|maret|mar|april|apr|mei|juni|jun|juli|jul|agustus|agu|september|sep|oktober|okt|november|nov|desember|des)\\s+(?:tanggal\\s+)?(\\d{1,2})(?:\\s+(\\d{4}))?\\b").find(text)
        if (namedMonthFirst != null) {
            val month = monthNumbers[namedMonthFirst.groupValues[1]] ?: return null
            val day = namedMonthFirst.groupValues[2].toIntOrNull() ?: return null
            var year = namedMonthFirst.groupValues[3].toIntOrNull() ?: base.get(Calendar.YEAR)
            var date = buildValidatedDate(day, month, year) ?: return null
            if (namedMonthFirst.groupValues[3].isBlank() && date.timeInMillis < base.startOfDayMillis()) {
                year += 1
                date = buildValidatedDate(day, month, year) ?: return null
            }
            return date
        }

        val dayOnly = Regex("\\btanggal\\s+(\\d{1,2})\\b").find(text)
        if (dayOnly != null) {
            val day = dayOnly.groupValues[1].toIntOrNull() ?: return null
            var month = base.get(Calendar.MONTH)
            var year = base.get(Calendar.YEAR)
            var date = buildValidatedDate(day, month, year) ?: return null
            if (date.timeInMillis < base.startOfDayMillis()) {
                month += 1
                if (month > Calendar.DECEMBER) {
                    month = Calendar.JANUARY
                    year += 1
                }
                date = buildValidatedDate(day, month, year) ?: return null
            }
            return date
        }
        return null
    }

    private fun buildValidatedDate(day: Int, month: Int, year: Int): Calendar? {
        if (day !in 1..31 || month !in Calendar.JANUARY..Calendar.DECEMBER) return null
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, 0, 0, 0)
            if (get(Calendar.YEAR) != year || get(Calendar.MONTH) != month || get(Calendar.DAY_OF_MONTH) != day) return null
        }
    }

    private fun resolveTime(text: String): Pair<Int, Int>? {
        val explicit = Regex("\\b(?:jam|pukul)\\s*(\\d{1,2})(?:[:.](\\d{2}))?\\s*(pagi|siang|sore|malam)?\\b").find(text)
        if (explicit != null) {
            var hour = explicit.groupValues[1].toIntOrNull() ?: return null
            val minute = explicit.groupValues[2].toIntOrNull() ?: 0
            val period = explicit.groupValues[3]
            if (hour !in 0..23 || minute !in 0..59) return null
            if (period.isNotBlank() && hour !in 1..12) return null
            when (period) {
                "pagi" -> if (hour == 12) hour = 0
                "siang" -> if (hour in 1..11) hour += 12
                "sore" -> if (hour in 1..11) hour += 12
                "malam" -> hour = when (hour) { 12 -> 0; in 1..11 -> hour + 12; else -> hour }
            }
            return hour to minute
        }
        val part = Regex("\\b(pagi|siang|sore|malam)\\b").find(text)?.value
        return when (part) {
            "siang" -> 13 to 0
            "sore" -> 16 to 0
            "malam" -> 19 to 0
            else -> DEFAULT_HOUR to 0
        }
    }

    private fun extractTitle(normalized: String): String {
        val withoutDateAndTime = normalized
            .replace(Regex("\\b(?:hari ini|nanti hari ini|nanti pagi|nanti siang|nanti sore|nanti malam|pagi ini|siang ini|sore ini|malam ini|dua hari lagi|besok|esok|lusa)\\b"), " ")
            .replace(Regex("\\b(?:senin|selasa|rabu|kamis|jumat|sabtu|minggu)(?:\\s+(?:ini|depan))?\\b"), " ")
            .replace(Regex("\\btanggal\\s+\\d{1,2}(?:\\s+(?:januari|jan|februari|feb|maret|mar|april|apr|mei|juni|jun|juli|jul|agustus|agu|september|sep|oktober|okt|november|nov|desember|des))?(?:\\s+\\d{4})?\\b"), " ")
            .replace(Regex("\\b\\d{1,2}[/-]\\d{1,2}(?:[/-]\\d{2,4})?\\b"), " ")
            .replace(Regex("\\b\\d{1,2}\\s+(?:januari|jan|februari|feb|maret|mar|april|apr|mei|juni|jun|juli|jul|agustus|agu|september|sep|oktober|okt|november|nov|desember|des)(?:\\s+\\d{4})?\\b"), " ")
            .replace(Regex("\\b(?:jam|pukul)\\s*\\d{1,2}(?:[:.]\\d{2})?\\s*(?:pagi|siang|sore|malam)?\\b"), " ")
        val stopWords = listOf(
            "aku", "saya", "gue", "gua", "kami", "kita", "ada", "akan", "bakal", "mau", "ingin",
            "nih", "loh", "ya", "dong", "beserta", "nanti", "punya", "acara", "ke", "di", "pada",
            "untuk", "sama", "dengan", "yang", "hari", "tanggal", "jam", "pukul", "pergi", "datang", "harus", "jangan", "lupa"
        )
        var title = withoutDateAndTime
        stopWords.forEach { word -> title = title.replace(Regex("\\b${Regex.escape(word)}\\b"), " ") }
        title = title.replace(Regex("\\s+"), " ").trim(' ', ',', '.', '!', '?', ':', ';')
        // Keep the source wording for names/case, while using normalized text for robust matching.
        val display = title.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.forLanguageTag("id-ID")) else it.toString() }
        return display.take(64).ifBlank {
            val matched = eventWords.firstOrNull { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(normalized) }
            matched?.replaceFirstChar { it.titlecase(Locale.forLanguageTag("id-ID")) } ?: "Agenda penting"
        }
    }

    private fun Calendar.startOfDayMillis(): Long = (clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun hasRelativeToday(text: String): Boolean = Regex("\\b(hari ini|nanti hari ini|nanti pagi|nanti siang|nanti sore|nanti malam|pagi ini|siang ini|sore ini|malam ini)\\b").containsMatchIn(text)
    private fun hasWeekday(text: String): Boolean = weekdayNumbers.keys.any { Regex("\\b${it}\\b").containsMatchIn(text) }
}
