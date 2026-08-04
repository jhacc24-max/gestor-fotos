package com.example.gestorfotos.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "albums")
data class Album(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Metadatos propios de la app para cada foto del MediaStore.
 * mediaStoreId es la clave que la vincula con la foto real del sistema.
 * Si no existe una fila aquí para una foto, se asume "sin clasificar, sin favorito, sin papelera".
 */
@Entity(tableName = "photo_meta")
data class PhotoMeta(
    @PrimaryKey val mediaStoreId: Long,
    val albumId: Long? = null,
    val isFavorite: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val rotationDegrees: Int = 0,
    val croppedUri: String? = null,
    val manualTags: String = "",       // separadas por coma
    val ocrText: String = "",          // texto detectado por ML Kit
    val perceptualHash: String? = null, // hash de 64 bits en binario, para duplicados
    val blurScore: Double? = null,      // varianza del laplaciano; bajo = borrosa
    val lastViewedAt: Long? = null,
    val analyzedAt: Long? = null,      // marca que ya se corrió OCR + hash + blur
    val needsDeleteConsent: Boolean = false // API < 30: el borrado directo chocó con RecoverableSecurityException
) {
    val searchableText: String get() = "$manualTags $ocrText".lowercase()
}

@Dao
interface AlbumDao {
    @Query("SELECT * FROM albums ORDER BY name ASC")
    fun observeAlbums(): Flow<List<Album>>

    @Insert
    suspend fun insert(album: Album): Long

    @Update
    suspend fun update(album: Album)

    @Delete
    suspend fun delete(album: Album)

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getById(id: Long): Album?
}

@Dao
interface PhotoMetaDao {
    @Query("SELECT * FROM photo_meta")
    fun observeAll(): Flow<List<PhotoMeta>>

    @Query("SELECT * FROM photo_meta WHERE mediaStoreId = :id")
    suspend fun getById(id: Long): PhotoMeta?

    @Query("SELECT * FROM photo_meta WHERE analyzedAt IS NULL LIMIT :limit")
    suspend fun getUnanalyzed(limit: Int = 25): List<PhotoMeta>

    @Query("SELECT * FROM photo_meta WHERE perceptualHash IS NOT NULL")
    suspend fun getAllWithHash(): List<PhotoMeta>

    @Query("SELECT * FROM photo_meta WHERE blurScore IS NOT NULL AND blurScore < :threshold")
    fun observeBlurry(threshold: Double = 60.0): Flow<List<PhotoMeta>>

    @Query("SELECT * FROM photo_meta WHERE isTrashed = 1 AND trashedAt < :cutoff")
    suspend fun getTrashedOlderThan(cutoff: Long): List<PhotoMeta>

    @Query("SELECT * FROM photo_meta WHERE needsDeleteConsent = 1")
    fun observeNeedingConsent(): Flow<List<PhotoMeta>>

    @Query("DELETE FROM photo_meta WHERE isTrashed = 1 AND trashedAt < :cutoff")
    suspend fun purgeOldTrash(cutoff: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: PhotoMeta)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(metas: List<PhotoMeta>)

    /** Inserta solo las filas que no existen todavía (no pisa metadatos ya guardados). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaults(metas: List<PhotoMeta>)

    @Query("DELETE FROM photo_meta WHERE mediaStoreId = :id")
    suspend fun delete(id: Long)
}

/**
 * Preferencias propias de la app para una CARPETA REAL del teléfono (no un álbum
 * propio): a qué grupo pertenece (ej. "Personal", "Trabajo") y si está marcada como
 * favorita. bucketId es el identificador de carpeta que entrega el MediaStore. Si no
 * hay fila para una carpeta, se asume sin grupo y no favorita.
 */
@Entity(tableName = "folder_meta")
data class FolderMeta(
    @PrimaryKey val bucketId: String,
    val groupName: String? = null,
    val isFavorite: Boolean = false
)

@Dao
interface FolderMetaDao {
    @Query("SELECT * FROM folder_meta")
    fun observeAll(): Flow<List<FolderMeta>>

    @Query("SELECT * FROM folder_meta WHERE bucketId = :bucketId")
    suspend fun getById(bucketId: String): FolderMeta?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(meta: FolderMeta)
}

@Database(entities = [Album::class, PhotoMeta::class, FolderMeta::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun photoMetaDao(): PhotoMetaDao
    abstract fun folderMetaDao(): FolderMetaDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gestor_fotos.db"
                )
                    .fallbackToDestructiveMigration() // prototipo: en producción, escribir migraciones reales
                    .build().also { INSTANCE = it }
            }
    }
}
