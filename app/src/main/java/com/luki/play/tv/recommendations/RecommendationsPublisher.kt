// tv/recommendations/RecommendationsPublisher.kt
package com.luki.play.tv.recommendations

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.ChannelLogoUtils
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.luki.play.data.catalog.domain.Channel as LukiChannel
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publica un canal "Recomendaciones Luki" en la home de Android TV usando la
 * TvProvider API (carril "tip" del launcher).
 *
 * Diseño:
 *  - Idempotente: si ya existe el canal, se actualiza; si no, se crea.
 *  - Los `previewPrograms` son los canales destacados pasados a [publish].
 *  - El deep link de cada item apunta a una URI propia que [com.luki.play.ui.RouterActivity]
 *    sabe rutear hacia [PlayerActivity] (a implementar en sub-fase 4.x).
 *
 * NOTA: TvProvider API requiere targetSdk ≥ 26 — ya cumplido.
 */
@Singleton
class RecommendationsPublisher @Inject constructor() {

    fun publish(context: Context, items: List<LukiChannel>) {
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Timber.tag(TAG).d("RecommendationsPublisher: API<26 — skip publish")
            return
        }
        runCatching {
            val channelId = ensureChannel(context)
            publishPrograms(context, channelId, items.take(MAX_ITEMS))
        }.onFailure {
            Timber.tag(TAG).w(it, "RecommendationsPublisher.publish failed")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun ensureChannel(context: Context): Long {
        val resolver = context.contentResolver
        // Localizar el canal "Luki Recomendados" si ya existe
        resolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            arrayOf(TvContractCompat.Channels._ID, TvContractCompat.Channels.COLUMN_INTERNAL_PROVIDER_ID),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val internalId = cursor.getString(1)
                if (internalId == INTERNAL_ID) return cursor.getLong(0)
            }
        }

        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName("Luki Recomendados")
            .setAppLinkIntentUri(Uri.parse("lukiplay://home"))
            .setInternalProviderId(INTERNAL_ID)
            .build()

        val uri = resolver.insert(
            TvContractCompat.Channels.CONTENT_URI,
            channel.toContentValues(),
        ) ?: error("No se pudo insertar el canal de recomendaciones")
        val id = ContentUris.parseId(uri)

        // Logo
        runCatching {
            ChannelLogoUtils.storeChannelLogo(
                context, id, android.graphics.BitmapFactory.decodeResource(
                    context.resources,
                    com.luki.play.R.drawable.banner_tv,
                ),
            )
        }

        TvContractCompat.requestChannelBrowsable(context, id)
        return id
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun publishPrograms(context: Context, channelId: Long, items: List<LukiChannel>) {
        val resolver = context.contentResolver
        // Borrar previews antiguos del canal
        resolver.delete(
            TvContractCompat.PreviewPrograms.CONTENT_URI,
            "${TvContractCompat.PreviewPrograms.COLUMN_CHANNEL_ID} = ?",
            arrayOf(channelId.toString()),
        )

        items.forEach { ch ->
            val program = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(TvContractCompat.PreviewPrograms.TYPE_CHANNEL)
                .setTitle(ch.name)
                .setDescription(ch.category)
                .apply {
                    ch.logoUrl?.let { setPosterArtUri(Uri.parse(it)) }
                }
                .setIntentUri(Uri.parse("lukiplay://play/${ch.id}"))
                .setInternalProviderId(ch.id)
                .build()

            resolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                program.toContentValues(),
            )
        }
    }

    companion object {
        private const val TAG = "RecommendationsPub"
        private const val INTERNAL_ID = "luki_recomendados"
        private const val MAX_ITEMS = 20
    }
}
