// test/data/catalog/api/StreamDtoTest.kt
package com.luki.play.data.catalog.api

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ancla el DTO al JSON literal que devuelve el backend.
 *
 * El DTO anterior pedía `url` (obligatorio) más `sessionId`,
 * `manifestType`, `drmScheme` y `licenseUrl`. Ninguno existe: el handler es
 * `return { streamUrl: canal.streamUrl }`. Moshi rompía con "Required value
 * 'url' missing" y no se reproducía NINGÚN canal — sin que ningún test lo
 * notara, porque el contrato nunca se había comprobado contra el backend.
 */
class StreamDtoTest {

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    @Test
    fun `deserializa la respuesta real del backend`() {
        // Copiado de public.controller.ts, getStreamUrl.
        val json = """{"streamUrl":"https://cdn.lukiplay.com/live/a3cine/index.m3u8"}"""

        val dto = moshi.adapter(StreamDto::class.java).fromJson(json)

        assertEquals(
            "https://cdn.lukiplay.com/live/a3cine/index.m3u8",
            dto?.streamUrl,
        )
    }
}
