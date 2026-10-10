package de.bolzwerk.tagwerk.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        NoteEntity::class,
        CategoryEntity::class,
        NoteCategoryEntity::class,
        AudioRecordingEntity::class,
        UsageLogEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class TagwerkDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun categoryDao(): CategoryDao
    abstract fun noteCategoryDao(): NoteCategoryDao
    abstract fun audioDao(): AudioDao
    abstract fun usageDao(): UsageDao

    companion object {
        /** Öffnet die mit SQLCipher verschlüsselte Datenbank. */
        fun create(context: Context, passphrase: ByteArray): TagwerkDatabase {
            System.loadLibrary("sqlcipher")
            return Room.databaseBuilder(context, TagwerkDatabase::class.java, "tagwerk.db")
                .openHelperFactory(SupportOpenHelperFactory(passphrase))
                .build()
        }
    }
}
