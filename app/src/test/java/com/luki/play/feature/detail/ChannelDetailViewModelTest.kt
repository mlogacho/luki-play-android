// test/feature/detail/ChannelDetailViewModelTest.kt
package com.luki.play.feature.detail

import androidx.lifecycle.SavedStateHandle
import com.luki.play.data.catalog.ChannelsRepository
import com.luki.play.data.catalog.api.CatalogApi
import com.luki.play.data.catalog.domain.Channel
import com.luki.play.data.catalog.domain.ContentType
import com.luki.play.data.parental.ParentalControl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * El punto que se comprueba aquí es el control parental.
 *
 * `requestPlay()` leía `state.channel` directamente y decidía en el acto. Si
 * la carga del canal aún no había terminado —cosa normal nada más entrar en
 * la pantalla— el canal era `null`, `null?.parentalLocked == true` daba
 * `false` y se pedía el stream SIN pedir el PIN. Bastaba con pulsar rápido
 * para saltarse el bloqueo.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChannelDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private val lockedChannel = Channel(
        id = "canal-1",
        name = "Canal bloqueado",
        logoUrl = null,
        category = "Adultos",
        type = ContentType.TV,
        parentalLocked = true,
    )

    private fun viewModel(
        repository: ChannelsRepository,
        api: CatalogApi,
        parental: ParentalControl,
    ) = ChannelDetailViewModel(
        repository = repository,
        api = api,
        parental = parental,
        savedStateHandle = SavedStateHandle(mapOf("channelId" to "canal-1")),
    )

    @Test
    fun `pedir play antes de que cargue el canal sigue exigiendo el PIN`() = runTest(dispatcher) {
        val repository = mockk<ChannelsRepository>()
        coEvery { repository.getChannelById("canal-1") } returns lockedChannel
        val api = mockk<CatalogApi>(relaxed = true)
        val parental = mockk<ParentalControl>()
        every { parental.hasPin() } returns true

        val vm = viewModel(repository, api, parental)
        // Sin avanzar el dispatcher: la carga lanzada en init sigue pendiente,
        // así que `state.channel` todavía es null — el escenario del fallo.
        vm.requestPlay()
        advanceUntilIdle()

        assertTrue(
            "un canal bloqueado debe exigir PIN aunque aun no hubiera cargado",
            vm.uiState.value.parentalGateRequired,
        )
        coVerify(exactly = 0) { api.getChannelStream(any()) }
    }

    @Test
    fun `sin PIN configurado un canal bloqueado se reproduce`() = runTest(dispatcher) {
        val repository = mockk<ChannelsRepository>()
        coEvery { repository.getChannelById("canal-1") } returns lockedChannel
        val api = mockk<CatalogApi>(relaxed = true)
        val parental = mockk<ParentalControl>()
        every { parental.hasPin() } returns false

        val vm = viewModel(repository, api, parental)
        vm.requestPlay()
        advanceUntilIdle()

        assertTrue(!vm.uiState.value.parentalGateRequired)
        coVerify(exactly = 1) { api.getChannelStream("canal-1") }
    }
}
