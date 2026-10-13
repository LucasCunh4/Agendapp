package br.com.agendapp.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.*
import br.com.agendapp.domain.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.preferences by preferencesDataStore(name="preferences")
@Entity(tableName="agenda") data class Document(@PrimaryKey val id: Int=1,val json: String)
@Dao interface AgendaDao {
    @Query("SELECT * FROM agenda WHERE id=1") fun observe(): Flow<Document?>
    @Query("SELECT * FROM agenda WHERE id=1") suspend fun get(): Document?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun put(document: Document)
}
@Database(entities=[Document::class],version=1,exportSchema=false)
abstract class AgendaDatabase: RoomDatabase() { abstract fun agenda(): AgendaDao }

class Repository(private val context: Context) {
    private val db=Room.databaseBuilder(context,AgendaDatabase::class.java,"agendapp.db").build()
    private val mutex=Mutex()
    private val settingsKey=stringPreferencesKey("settings_v1")
    val backup: Flow<Backup> = db.agenda().observe().map { it?.let { d -> BackupCodec.decode(d.json) } ?: Backup() }.distinctUntilChanged()
    val settings: Flow<Settings> = context.preferences.data.map { preferences -> preferences[settingsKey]?.let { kotlinx.serialization.json.Json.decodeFromString<Settings>(it) } ?: Settings() }
    suspend fun current(): Backup = db.agenda().get()?.let { BackupCodec.decode(it.json) } ?: Backup()
    suspend fun change(transform: (Backup)->Backup) = mutex.withLock {
        val next=db.withTransaction {
            val result=transform(current()); BackupCodec.validate(result)
            db.agenda().put(Document(json=BackupCodec.encode(result))); result
        }
        mirror(next.settings)
    }
    suspend fun initialize() { change { it.copy(state=Engine.expire(it.state,System.currentTimeMillis())) } }
    private suspend fun mirror(settings: Settings) { context.preferences.edit { it[settingsKey]=kotlinx.serialization.json.Json.encodeToString(Settings.serializer(),settings) } }
}
