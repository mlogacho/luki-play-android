// data/favorites/FavoritesRepository.kt
package com.luki.play.data.favorites

import com.luki.play.data.auth.TokenStore
import com.luki.play.data.favorites.api.AddFavoriteRequest
import com.luki.play.data.favorites.api.FavoritesApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favoritos del usuario, como conjunto de ids de canal.
 *
 * Estrategia calcada del portal (`useChannels.toggleFavorite`): la marca se
 * aplica **de inmediato en memoria** para que la UI responda al instante y,
 * si el servidor rechaza la escritura, se revierte. Sin esa reversión un
 * POST fallido pasaba desapercibido y el corazón volvía solo en el
 * siguiente refresco, que es justo el fallo que el portal documenta.
 *
 * `profileId` va fijo a `__default__`: el selector de perfiles no existe ni
 * en el portal ni en el arranque nativo.
 */
@Singleton
class FavoritesRepository internal constructor(
    private val api: FavoritesApi,
    private val tokenStore: TokenStore,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject
    constructor(api: FavoritesApi, tokenStore: TokenStore) :
        this(api, tokenStore, Dispatchers.IO)

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())

    /** Ids de canal marcados como favoritos. */
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    /**
     * Recarga desde el servidor. Ante error deja el conjunto actual intacto
     * en vez de vaciarlo: un fallo de red no significa "no hay favoritos", y
     * borrarlos haría desaparecer la lista del usuario.
     */
    suspend fun refresh() = withContext(ioDispatcher) {
        runCatching {
            api.getFavorites(tokenStore.deviceId(), DEFAULT_PROFILE_ID)
        }.onSuccess { ids ->
            _favorites.value = ids.toSet()
        }.onFailure {
            Timber.w(it, "FavoritesRepository: no se pudo refrescar la lista")
        }
        Unit
    }

    /**
     * Marca o desmarca un canal.
     *
     * @return true si el servidor confirmó el cambio. En false la marca ya
     *   se ha revertido, así que la UI no necesita hacer nada más.
     */
    suspend fun toggle(channelId: String, favorite: Boolean): Boolean =
        withContext(ioDispatcher) {
            val previous = _favorites.value
            _favorites.value =
                if (favorite) previous + channelId else previous - channelId

            val deviceId = tokenStore.deviceId()
            val confirmed = runCatching {
                if (favorite) {
                    val response = api.addFavorite(
                        channelId = channelId,
                        body = AddFavoriteRequest(deviceId, DEFAULT_PROFILE_ID),
                    )
                    // 409 = ya estaba marcado; el estado pedido ya se cumple.
                    response.isSuccessful || response.code() == HTTP_CONFLICT
                } else {
                    val response = api.removeFavorite(
                        channelId = channelId,
                        deviceId = deviceId,
                        profileId = DEFAULT_PROFILE_ID,
                    )
                    // 404 = ya no estaba; idem.
                    response.isSuccessful || response.code() == HTTP_NOT_FOUND
                }
            }.getOrElse {
                Timber.w(it, "FavoritesRepository: error al marcar favorito")
                false
            }

            if (!confirmed) _favorites.value = previous
            confirmed
        }

    /** Vacía la lista en memoria; se llama al cerrar sesión. */
    fun clear() {
        _favorites.value = emptySet()
    }

    private companion object {
        const val DEFAULT_PROFILE_ID = "__default__"
        const val HTTP_CONFLICT = 409
        const val HTTP_NOT_FOUND = 404
    }
}
