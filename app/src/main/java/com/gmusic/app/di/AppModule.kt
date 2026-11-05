package com.gmusic.app.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.gmusic.app.data.local.GMusicDao
import com.gmusic.app.data.local.GMusicDatabase
import com.gmusic.app.data.stream.ChainedStreamResolver
import com.gmusic.app.data.stream.StreamResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class StreamBindingModule {
    @Binds
    @Singleton
    abstract fun bindStreamResolver(impl: ChainedStreamResolver): StreamResolver
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `saved_artists` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `subscribersText` TEXT, `thumbnailUrl` TEXT NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): GMusicDatabase =
        Room.databaseBuilder(
            context,
            GMusicDatabase::class.java,
            "gmusic_db"
        )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideDao(db: GMusicDatabase): GMusicDao = db.dao()

    @OptIn(UnstableApi::class)
    @Provides
    @Singleton
    fun provideSimpleCache(@ApplicationContext context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "gmusic_audio_cache")
        val evictor = LeastRecentlyUsedCacheEvictor(512L * 1024L * 1024L) // 512 MB LRU
        val dbProvider = StandaloneDatabaseProvider(context)
        return SimpleCache(cacheDir, evictor, dbProvider)
    }
}
