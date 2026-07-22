// test/data/catalog/ChannelsRepositoryTest.kt
package com.luki.play.data.catalog

import app.cash.turbine.test
import com.luki.play.data.catalog.api.CatalogApi
import com.luki.play.data.catalog.api.ChannelDto
import com.luki.play.data.catalog.api.SliderDto
import com.luki.play.data.catalog.api.StreamDto
import com.luki.play.data.catalog.db.CatalogDao
import com.luki.play.data.catalog.db.ChannelEntity
import com.luki.play.data.catalog.domain.ContentType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ChannelsRepositoryTest {

    @Test
    fun `refresh persists API result into DAO`() = runTest {
        val api = FakeCatalogApi(
            channels = listOf(
                ChannelDto("1", "ECTV", "/logo.png", "Nacionales", "tv", false),
                ChannelDto("2", "Cine24", null, "Películas", "movie", true),
            )
        )
        val dao = FakeCatalogDao()
        val repo = ChannelsRepository(api, dao)

        val result = repo.refresh()

        assertTrue(result.isSuccess)
        assertEquals(2, dao.stored.size)
        val first = dao.stored.first { it.id == "1" }
        assertEquals("ECTV", first.name)
        assertEquals("https://lukiplay.com/logo.png", first.logoUrl)
    }

    @Test
    fun `observeChannels maps entities to domain`() = runTest {
        val dao = FakeCatalogDao(
            initial = listOf(
                entity("1", "X", "Live", "tv", false),
                entity("2", "Y", "Kids", "series", true),
            )
        )
        val repo = ChannelsRepository(FakeCatalogApi(), dao)

        repo.observeChannels().test {
            val first = awaitItem()
            assertEquals(2, first.size)
            assertEquals(ContentType.TV, first[0].type)
            assertEquals(ContentType.SERIES, first[1].type)
            assertTrue(first[1].parentalLocked)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh failure returns Result failure without touching DAO`() = runTest {
        val api = FakeCatalogApi(channelsException = IOException("offline"))
        val dao = FakeCatalogDao()
        val repo = ChannelsRepository(api, dao)

        val result = repo.refresh()

        assertTrue(result.isFailure)
        assertEquals(0, dao.stored.size)
    }

    @Test
    fun `searchChannels delegates to DAO with query`() = runTest {
        val dao = FakeCatalogDao(
            initial = listOf(
                entity("1", "ECTV", "Live", "tv", false),
                entity("2", "RTS", "Live", "tv", false),
            )
        )
        val repo = ChannelsRepository(FakeCatalogApi(), dao)

        repo.searchChannels("ECTV").test {
            val matched = awaitItem()
            assertEquals(1, matched.size)
            assertEquals("ECTV", matched[0].name)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun entity(id: String, name: String, category: String, type: String, parental: Boolean) =
        ChannelEntity(id, name, null, category, type, parental, 0L)
}

private class FakeCatalogApi(
    private val channels: List<ChannelDto> = emptyList(),
    private val sliders: List<SliderDto> = emptyList(),
    private val channelsException: Throwable? = null,
) : CatalogApi {
    override suspend fun getChannels(): List<ChannelDto> {
        channelsException?.let { throw it }
        return channels
    }
    override suspend fun getSliders(): List<SliderDto> = sliders
    override suspend fun getChannelStream(channelId: String): StreamDto =
        StreamDto("https://x/y.m3u8")
}

private class FakeCatalogDao(
    initial: List<ChannelEntity> = emptyList(),
) : CatalogDao {
    val stored = mutableListOf<ChannelEntity>().apply { addAll(initial) }
    private val flow = MutableStateFlow<List<ChannelEntity>>(initial)

    override fun observeChannels(): Flow<List<ChannelEntity>> = flow.asStateFlow()

    override suspend fun findById(id: String): ChannelEntity? = stored.firstOrNull { it.id == id }

    override fun searchChannels(query: String): Flow<List<ChannelEntity>> =
        MutableStateFlow(stored.filter { it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true) }).asStateFlow()

    override suspend fun upsertAll(items: List<ChannelEntity>) {
        items.forEach { item ->
            stored.removeAll { it.id == item.id }
            stored += item
        }
        flow.value = stored.toList()
    }

    override suspend fun deleteStale(olderThanMs: Long) {
        stored.removeAll { it.updatedAtMs < olderThanMs }
        flow.value = stored.toList()
    }

    override suspend fun clear() {
        stored.clear()
        flow.value = emptyList()
    }
}
