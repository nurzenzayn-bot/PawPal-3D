package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.notifications.ConversationReminderScheduler
import com.example.notifications.ReminderTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ConversationReminderSchedulerTest {
    private fun fixedNow(): Calendar = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.NOVEMBER, 16, 14, 0, 0)
    }

    @Test
    fun `captures tomorrow family wedding and persists it`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pawpal_conversation_reminders", Context.MODE_PRIVATE).edit().clear().apply()
        ConversationReminderScheduler.setEnabled(context, true)

        val reminder = ConversationReminderScheduler.captureFromMessage(
            context,
            "Besok aku ada acara nikahan di keluarga loh"
        )

        val captured = requireNotNull(reminder)
        assertEquals("Nikahan keluarga", captured.title)
        assertTrue(captured.at > System.currentTimeMillis())
        assertEquals(captured.id, ConversationReminderScheduler.all(context).single().id)
    }

    @Test
    fun `does not capture hypothetical or cancelled agenda`() {
        val now = fixedNow().timeInMillis
        assertNull(ReminderTextParser.parse("Kalau besok ada acara nikahan mungkin aku datang", now))
        assertNull(ReminderTextParser.parse("Besok aku tidak ada acara", now))
        assertNull(ReminderTextParser.parse("Besok aku tidak jadi ke rapat", now))
        assertNull(ReminderTextParser.parse("Besok mungkin hujan", now))
    }

    @Test
    fun `parses weekday and explicit afternoon time`() {
        val parsed = ReminderTextParser.parse("Jumat depan aku ada rapat jam 19.30", fixedNow().timeInMillis)
        assertNotNull(parsed)
        val calendar = Calendar.getInstance().apply { timeInMillis = parsed!!.at }
        assertEquals(Calendar.FRIDAY, calendar.get(Calendar.DAY_OF_WEEK))
        assertEquals(19, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar.get(Calendar.MINUTE))
    }

    @Test
    fun `converts twelve at night to midnight`() {
        val parsed = ReminderTextParser.parse("Besok ada janji dokter pukul 12 malam", fixedNow().timeInMillis)
        assertNotNull(parsed)
        val calendar = Calendar.getInstance().apply { timeInMillis = requireNotNull(parsed).at }
        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `parses Indonesian month date and daypart`() {
        val parsed = ReminderTextParser.parse("Tanggal 22 November ada acara wisuda malam", fixedNow().timeInMillis)
        assertNotNull(parsed)
        val calendar = Calendar.getInstance().apply { timeInMillis = parsed!!.at }
        assertEquals(22, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.NOVEMBER, calendar.get(Calendar.MONTH))
        assertEquals(19, calendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `parses tonight as a same day evening reminder`() {
        val parsed = ReminderTextParser.parse("Malam ini ada rapat keluarga", fixedNow().timeInMillis)
        assertNotNull(parsed)
        val calendar = Calendar.getInstance().apply { timeInMillis = requireNotNull(parsed).at }
        assertEquals(16, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(19, calendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `respects an explicit year in a named date`() {
        val parsed = ReminderTextParser.parse("22 November 2027 ada acara wisuda", fixedNow().timeInMillis)
        assertNotNull(parsed)
        val calendar = Calendar.getInstance().apply { timeInMillis = requireNotNull(parsed).at }
        assertEquals(2027, calendar.get(Calendar.YEAR))
        assertEquals(Calendar.NOVEMBER, calendar.get(Calendar.MONTH))
    }

    @Test
    fun `can remove and snooze a saved reminder`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("pawpal_conversation_reminders", Context.MODE_PRIVATE).edit().clear().apply()
        ConversationReminderScheduler.setEnabled(context, true)
        val created = requireNotNull(
            ConversationReminderScheduler.captureFromMessage(context, "Besok ada acara nikahan keluarga")
        )

        ConversationReminderScheduler.snooze(context, created.id, created.title, created.body)
        val snoozed = ConversationReminderScheduler.all(context).single()
        assertTrue(snoozed.id != created.id)
        assertTrue(snoozed.at > System.currentTimeMillis())
        assertTrue(snoozed.title.contains("nikahan", ignoreCase = true))

        ConversationReminderScheduler.remove(context, snoozed.id)
        assertTrue(ConversationReminderScheduler.all(context).isEmpty())
    }


    @Test
    fun `rejects impossible clock times instead of silently clamping them`() {
        val now = fixedNow().timeInMillis
        assertNull(ReminderTextParser.parse("Besok ada rapat pukul 25.99", now))
        assertNull(ReminderTextParser.parse("Besok ada janji dokter jam 24", now))
    }

    @Test
    fun `treats next week as seven days rather than next Sunday`() {
        val parsed = ReminderTextParser.parse("Minggu depan ada acara keluarga", fixedNow().timeInMillis)
        assertNotNull(parsed)
        val calendar = Calendar.getInstance().apply { timeInMillis = requireNotNull(parsed).at }
        assertEquals(23, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.NOVEMBER, calendar.get(Calendar.MONTH))
    }

    @Test
    fun `parses slash date`() {
        val parsed = ReminderTextParser.parse("22/11/2026 ada konser", fixedNow().timeInMillis)
        assertNotNull(parsed)
        val calendar = Calendar.getInstance().apply { timeInMillis = parsed!!.at }
        assertEquals(22, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.NOVEMBER, calendar.get(Calendar.MONTH))
        assertEquals(2026, calendar.get(Calendar.YEAR))
    }
    @Test
    fun `parses pet care reminder with explicit time`() {
        val parsed = ReminderTextParser.parse("Ingatkan aku memberi makan jam 19:30", fixedNow().timeInMillis)
        assertNotNull(parsed)
        assertEquals("Waktunya memberi makan", parsed!!.title)
        val calendar = Calendar.getInstance().apply { timeInMillis = parsed.at }
        assertEquals(19, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, calendar.get(Calendar.MINUTE))
        assertEquals(16, calendar.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun `moves past pet care time to tomorrow`() {
        val now = fixedNow().apply { set(Calendar.HOUR_OF_DAY, 20) }.timeInMillis
        val parsed = ReminderTextParser.parse("Main jam 8 malam", now)
        assertNotNull(parsed)
        assertEquals("Waktunya bermain", parsed!!.title)
        val calendar = Calendar.getInstance().apply { timeInMillis = parsed.at }
        assertEquals(17, calendar.get(Calendar.DAY_OF_MONTH))
        assertEquals(20, calendar.get(Calendar.HOUR_OF_DAY))
    }

    @Test
    fun `rejects pet care reminder without explicit clock time`() {
        assertNull(ReminderTextParser.parse("Ingatkan aku memberi makan nanti", fixedNow().timeInMillis))
    }

    @Test
    fun `parses pet care intent labels`() {
        val now = fixedNow().timeInMillis
        assertEquals("Waktunya bersih-bersih", ReminderTextParser.parse("Mandi jam 16", now)?.title)
        assertEquals("Cek air minum", ReminderTextParser.parse("Cek minum jam 17", now)?.title)
        assertEquals("Waktunya istirahat", ReminderTextParser.parse("Tidur jam 21", now)?.title)
    }

    @Test
    fun `keeps ordinary meal event wording as an agenda title`() {
        val parsed = ReminderTextParser.parse("Besok ada acara makan malam keluarga", fixedNow().timeInMillis)
        assertNotNull(parsed)
        assertEquals("Makan malam keluarga", parsed!!.title)
    }

}
