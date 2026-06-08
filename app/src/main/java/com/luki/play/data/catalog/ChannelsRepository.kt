// data/catalog/ChannelsRepository.kt
package com.luki.play.data.catalog

import com.luki.play.data.catalog.api.CatalogApi
import com.luki.play.data.catalog.api.ChannelDto
import com.luki.play.data.catalog.api.SliderDto
import com.luki.play.data.catalog.db.CatalogDao
import com.luki.play.data.catalog.db.ChannelEntity
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.data.catalog.domain.ContentType
import com.luki.play.data.catalog.domain.Slider
import com.luki.play.data.catalog.domain.SliderAction
import com.luki.play.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositorio del catálogo público.
 *
 * Estrategia: **single source of truth = Room**. El UI observa la base; las
 * peticiones HTTP rellenan/actualizan la base y devuelven [Result].
 *
 * Si la red falla, el observable sigue emitiendo lo que haya en caché — la
 * pantalla puede mostrar contenido viejo + un toast de "modo offline".
 */
@Singleton
class ChannelsRepository @Inject constructor(
    private val api: CatalogApi,
    private val dao: CatalogDao,
) {

    fun observeChannels(): Flow<List<Channel>> =
        dao.observeChannels().map { list -> list.map { it.toDomain() } }

    fun searchChannels(query: String): Flow<List<Channel>> =
        dao.searchChannels(query.trim()).map { list -> list.map { it.toDomain() } }

    suspend fun getChannelById(id: String): Channel? = withContext(Dispatchers.IO) {
        dao.findById(id)?.toDomain()
    }

    /**
     * Refresca el catálogo desde el backend y lo guarda en Room.
     * No lanza: devuelve [Result] para que el UI decida cómo notificarlo.
     */
    suspend fun refresh(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val remote = api.getChannels()
            dao.upsertAll(remote.map { it.toEntity(now) })
            // Limpia entradas que llevan >7 días sin actualizarse (canales eliminados upstream).
            dao.deleteStale(now - SEVEN_DAYS_MS)
        }.onFailure { Timber.tag(TAG).w(it, "ChannelsRepository.refresh failed") }
    }

    suspend fun sliders(): Result<List<Slider>> = withContext(Dispatchers.IO) {
        runCatching { api.getSliders().map { it.toDomain() } }
            .onFailure { Timber.tag(TAG).w(it, "ChannelsRepository.sliders failed") }
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private fun ChannelDto.toEntity(now: Long) = ChannelEntity(
        id              = id,
        name            = name,
        logoUrl         = absoluteUrl(logo),
        category        = category ?: "General",
        type            = type ?: "tv",
        parentalLocked  = requiresParental ?: false,
        updatedAtMs     = now,
    )

    private fun ChannelEntity.toDomain() = Channel(
        id              = id,
        name            = name,
        logoUrl         = logoUrl,
        category        = category,
        type            = parseContentType(type),
        parentalLocked  = parentalLocked,
    )

    private fun SliderDto.toDomain() = Slider(
        id       = id,
        title    = title,
        subtitle = subtitle,
        imageUrl = absoluteUrl(image),
        action   = when (actionType?.uppercase()) {
            "OPEN_CHANNEL" -> actionValue?.let { SliderAction.OpenChannel(it) } ?: SliderAction.None
            "OPEN_URL"     -> actionValue?.let { SliderAction.OpenUrl(it) }     ?: SliderAction.None
            else           -> SliderAction.None
        },
    )

    private fun parseContentType(raw: String): ContentType = when (raw.lowercase()) {
        "tv"     -> ContentType.TV
        "movie", "pelicula" -> ContentType.MOVIE
        "series" -> ContentType.SERIES
        "radio"  -> ContentType.RADIO
        else     -> ContentType.OTHER
    }

    private fun absoluteUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        return if (path.startsWith("http")) path else "${Constants.SERVER_BASE}$path"
    }

    companion object {
        private const val TAG = "ChannelsRepo"
        private const val SEVEN_DAYS_MS = 7L * 24 * 60 * 60 * 1000
    }
}
