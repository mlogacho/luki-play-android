// data/catalog/db/LukiDatabase.kt
package com.luki.play.data.catalog.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Base Room consolidada de la app. Por ahora sólo cachea el catálogo;
 * futuras fases (descargas offline, perfiles) sumarán tablas aquí.
 */
@Database(
    entities = [ChannelEntity::class],
    version  = 1,
    exportSchema = false,
)
abstract class LukiDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
