// data/catalog/db/CatalogDao.kt
package com.luki.play.data.catalog.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Caché Room del catálogo público.
 *
 * Sólo metadatos (sin contenido) — sirve para arrancar offline y para que
 * la Home no muestre pantalla en blanco mientras la red responde.
 */
@Entity(tableName = "channels")
data class ChannelEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name")             val name: String,
    @ColumnInfo(name = "logo_url")         val logoUrl: String?,
    @ColumnInfo(name = "category")         val category: String,
    @ColumnInfo(name = "type")             val type: String,
    @ColumnInfo(name = "parental_locked") val parentalLocked: Boolean,
    @ColumnInfo(name = "updated_at_ms")   val updatedAtMs: Long,
)

@Dao
interface CatalogDao {

    @Query("SELECT * FROM channels ORDER BY category, name")
    fun observeChannels(): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE name LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY name LIMIT 100")
    fun searchChannels(query: String): Flow<List<ChannelEntity>>

    @Upsert
    suspend fun upsertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE updated_at_ms < :olderThanMs")
    suspend fun deleteStale(olderThanMs: Long)

    @Query("DELETE FROM channels")
    suspend fun clear()
}
