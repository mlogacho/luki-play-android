// data/devices/DevicesRepository.kt
package com.luki.play.data.devices

import android.os.Build
import com.luki.play.data.auth.TokenStore
import com.luki.play.data.devices.api.DeviceItemDto
import com.luki.play.data.devices.api.DevicesApi
import com.luki.play.data.devices.api.RegisterDeviceRequest
import com.luki.play.data.devices.api.RenameDeviceRequest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class DeviceType {
    MOBILE, TABLET, DESKTOP, SMART_TV, UNKNOWN;

    companion object {
        fun from(raw: String?): DeviceType = when (raw?.uppercase()) {
            "MOBILE" -> MOBILE
            "TABLET" -> TABLET
            "DESKTOP" -> DESKTOP
            "SMART_TV" -> SMART_TV
            else -> UNKNOWN
        }
    }
}

/** Un dispositivo registrado (`DeviceListItem` del portal). */
data class Device(
    val id: String,
    val fingerprint: String,
    val nombre: String?,
    val tipo: DeviceType,
    val os: String?,
    val browser: String?,
    val ipAddress: String?,
    val lastSeenAt: String?,
    val isCurrentDevice: Boolean,
)

/** Lista de dispositivos + tope del plan (`DevicesResponse`). */
data class DeviceList(
    val devices: List<Device>,
    val limit: Int,
)

/**
 * Repositorio de dispositivos. Replica el flujo de `devices.tsx`: antes de
 * listar, registra ESTE dispositivo (best-effort) con el deviceId canónico
 * —el mismo que usa el login— para que aparezca marcado como "Este" y no se
 * duplique un cupo del `deviceLimitPolicy`.
 */
@Singleton
class DevicesRepository internal constructor(
    private val api: DevicesApi,
    private val tokenStore: TokenStore,
    private val ioDispatcher: CoroutineDispatcher,
) {

    @Inject constructor(api: DevicesApi, tokenStore: TokenStore) :
        this(api, tokenStore, Dispatchers.IO)

    suspend fun load(): Result<DeviceList> = withContext(ioDispatcher) {
        runCatching {
            val fingerprint = tokenStore.deviceId()
            // Registro idempotente en el backend (upsert por fingerprint); si
            // falla no debe impedir listar, igual que el portal.
            runCatching { api.register(registerBody(fingerprint)) }
                .onFailure { Timber.w(it, "DevicesRepository: register best-effort falló") }

            val resp = api.getDevices(fingerprint)
            DeviceList(
                devices = resp.devices.map { it.toDomain() },
                limit = resp.limit,
            )
        }.onFailure { Timber.w(it, "DevicesRepository: load falló") }
    }

    suspend fun rename(fingerprint: String, nombre: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching { api.rename(fingerprint, RenameDeviceRequest(nombre)) }
            .onFailure { Timber.w(it, "DevicesRepository: rename falló") }
    }

    suspend fun remove(fingerprint: String): Result<Unit> = withContext(ioDispatcher) {
        runCatching { api.remove(fingerprint, tokenStore.deviceId()) }
            .onFailure { Timber.w(it, "DevicesRepository: remove falló") }
    }

    private fun registerBody(fingerprint: String) = RegisterDeviceRequest(
        deviceFingerprint = fingerprint,
        tipo = "MOBILE",
        os = "Android ${Build.VERSION.RELEASE}",
        // El portal manda el navegador aquí; en la app enviamos el modelo, que
        // es lo que hace legible la línea inferior de la tarjeta.
        browser = Build.MODEL,
        nombre = null,
    )

    private fun DeviceItemDto.toDomain(): Device = Device(
        id = id.orEmpty(),
        fingerprint = deviceFingerprint.orEmpty(),
        nombre = nombre,
        tipo = DeviceType.from(tipo),
        os = os,
        browser = browser,
        ipAddress = ipAddress,
        lastSeenAt = lastSeenAt,
        isCurrentDevice = isCurrentDevice,
    )
}
