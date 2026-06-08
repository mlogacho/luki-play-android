// test/data/parental/ParentalControlTest.kt
package com.luki.play.data.parental

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import com.luki.play.util.SecureStorage
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests del [ParentalControl] sin instrumentación: mockeamos [SecureStorage.prefs]
 * para devolver un [InMemorySharedPreferences].
 */
class ParentalControlTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs = InMemorySharedPreferences()

    @Before fun setUp() {
        mockkObject(SecureStorage)
        every { SecureStorage.prefs(any()) } returns prefs
    }

    @After fun tearDown() { unmockkAll() }

    @Test
    fun `hasPin is false initially`() {
        val pc = ParentalControl(context)
        assertFalse(pc.hasPin())
    }

    @Test
    fun `setPin then verify works for correct PIN`() {
        val pc = ParentalControl(context)
        pc.setPin("1234")
        assertTrue(pc.hasPin())
        assertTrue(pc.verify("1234"))
    }

    @Test
    fun `verify rejects wrong PIN`() {
        val pc = ParentalControl(context)
        pc.setPin("9876")
        assertFalse(pc.verify("0000"))
        assertFalse(pc.verify("987"))
    }

    @Test
    fun `setPin rejects non-numeric or wrong length`() {
        val pc = ParentalControl(context)
        runCatching { pc.setPin("12") }.let { assertTrue(it.isFailure) }
        runCatching { pc.setPin("abcd") }.let { assertTrue(it.isFailure) }
        runCatching { pc.setPin("1234567") }.let { assertTrue(it.isFailure) }
    }

    @Test
    fun `clear removes pin`() {
        val pc = ParentalControl(context)
        pc.setPin("4321")
        pc.clear()
        assertFalse(pc.hasPin())
        assertFalse(pc.verify("4321"))
    }
}

/**
 * SharedPreferences en-memoria minimal — sólo implementamos lo que ParentalControl usa.
 */
private class InMemorySharedPreferences : SharedPreferences {
    private val map = mutableMapOf<String, String?>()

    override fun getString(key: String?, defValue: String?): String? = map[key] ?: defValue
    override fun edit(): SharedPreferences.Editor = InMemoryEditor(map)
    override fun contains(key: String?): Boolean = map.containsKey(key)

    override fun getAll(): MutableMap<String, *> = map.toMutableMap()
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
}

private class InMemoryEditor(private val map: MutableMap<String, String?>) : SharedPreferences.Editor {
    override fun putString(key: String?, value: String?): SharedPreferences.Editor { if (key != null) map[key] = value; return this }
    override fun remove(key: String?): SharedPreferences.Editor { if (key != null) map.remove(key); return this }
    override fun clear(): SharedPreferences.Editor { map.clear(); return this }
    override fun commit(): Boolean = true
    override fun apply() {}
    override fun putStringSet(k: String?, v: MutableSet<String>?): SharedPreferences.Editor = this
    override fun putInt(k: String?, v: Int): SharedPreferences.Editor = this
    override fun putLong(k: String?, v: Long): SharedPreferences.Editor = this
    override fun putFloat(k: String?, v: Float): SharedPreferences.Editor = this
    override fun putBoolean(k: String?, v: Boolean): SharedPreferences.Editor = this
}
