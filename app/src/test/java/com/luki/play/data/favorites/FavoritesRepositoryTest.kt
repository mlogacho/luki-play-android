// test/data/favorites/FavoritesRepositoryTest.kt
package com.luki.play.data.favorites

import com.luki.play.data.auth.FakeTokenStore
import com.luki.play.data.favorites.api.AddFavoriteRequest
import com.luki.play.data.favorites.api.FavoritesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * El punto delicado es el update optimista: la marca se aplica antes de que
 * el servidor conteste, así que un fallo TIENE que revertirla. Sin eso el
 * corazón se queda encendido mintiendo hasta el siguiente refresco.
 */
class FavoritesRepositoryTest {

    private class FakeFavoritesApi(
        var stored: List<String> = emptyList(),
        var addCode: Int = 200,
        var removeCode: Int = 200,
        var throwOnRead: Boolean = false,
    ) : FavoritesApi {

        override suspend fun getFavorites(deviceId: String, profileId: String): List<String> {
            if (throwOnRead) throw RuntimeException("sin red")
            return stored
        }

        override suspend fun addFavorite(
            channelId: String,
            body: AddFavoriteRequest,
        ): Response<Unit> = response(addCode)

        override suspend fun removeFavorite(
            channelId: String,
            deviceId: String,
            profileId: String,
        ): Response<Unit> = response(removeCode)

        private fun response(code: Int): Response<Unit> =
            if (code in 200..299) Response.success(Unit)
            else Response.error(code, "".toResponseBody("text/plain".toMediaType()))
    }

    /**
     * El dispatcher comparte el scheduler de `runTest`: con uno propio,
     * kotlinx-coroutines aborta con "Detected use of different schedulers".
     */
    private fun TestScope.repository(api: FavoritesApi) = FavoritesRepository(
        api = api,
        tokenStore = FakeTokenStore(),
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )

    @Test
    fun `marcar favorito con exito deja el canal en la lista`() = runTest {
        val repo = repository(FakeFavoritesApi())

        assertTrue(repo.toggle("canal-1", favorite = true))
        assertEquals(setOf("canal-1"), repo.favorites.value)
    }

    @Test
    fun `un fallo del servidor revierte la marca`() = runTest {
        val repo = repository(FakeFavoritesApi(addCode = 500))

        assertFalse(repo.toggle("canal-1", favorite = true))
        assertTrue(
            "la marca optimista debe deshacerse tras el error",
            repo.favorites.value.isEmpty(),
        )
    }

    @Test
    fun `409 al agregar cuenta como exito porque ya era favorito`() = runTest {
        val repo = repository(FakeFavoritesApi(addCode = 409))

        assertTrue(repo.toggle("canal-1", favorite = true))
        assertEquals(setOf("canal-1"), repo.favorites.value)
    }

    @Test
    fun `404 al quitar cuenta como exito porque ya no era favorito`() = runTest {
        val api = FakeFavoritesApi(stored = listOf("canal-1"), removeCode = 404)
        val repo = repository(api)
        repo.refresh()

        assertTrue(repo.toggle("canal-1", favorite = false))
        assertTrue(repo.favorites.value.isEmpty())
    }

    @Test
    fun `un refresh fallido conserva la lista en vez de vaciarla`() = runTest {
        val api = FakeFavoritesApi(stored = listOf("canal-1"))
        val repo = repository(api)
        repo.refresh()
        assertEquals(setOf("canal-1"), repo.favorites.value)

        api.throwOnRead = true
        repo.refresh()

        assertEquals(
            "un error de red no significa que el usuario no tenga favoritos",
            setOf("canal-1"),
            repo.favorites.value,
        )
    }
}
