package com.example.livora.data.people

import android.content.Context
import com.example.livora.data.people.db.PeopleDatabase
import com.example.livora.data.people.ml.FaceAnalyzer
import com.example.livora.data.people.scan.GalleryScanner
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AnalyzerHolder(private val context: Context) {

    private val mutex = Mutex()
    private var analyzer: FaceAnalyzer? = null

    suspend fun <T> use(block: (FaceAnalyzer) -> T): T = mutex.withLock {
        val current = analyzer ?: FaceAnalyzer(context.applicationContext).also { analyzer = it }
        block(current)
    }

    suspend fun release() = mutex.withLock {
        analyzer?.close()
        analyzer = null
    }
}

object EnrollDraft {
    var uris: List<android.net.Uri> = emptyList()
    var personId: Long? = null

    fun set(uris: List<android.net.Uri>, personId: Long? = null) {
        this.uris = uris
        this.personId = personId
    }

    fun take(): List<android.net.Uri> {
        val out = uris
        uris = emptyList()
        return out
    }
}

class PeopleServices private constructor(context: Context) {

    val app: Context = context.applicationContext
    val database: PeopleDatabase = PeopleDatabase.create(app)
    val prefs = PeoplePrefs(app)
    val analyzer = AnalyzerHolder(app)
    val clustering = ClusteringService(database, prefs)
    val folders = FoldersRepository(app, database)
    val repository = PeopleRepository(app, database, prefs, clustering, analyzer, folders)
    val copyRunner = CopyRunner(repository, folders)
    val backup = com.example.livora.data.people.backup.PeopleBackup(app, database, prefs)
    val scanner = GalleryScanner(app, database, prefs, analyzer, clustering, repository)

    companion object {
        @Volatile
        private var instance: PeopleServices? = null

        fun get(context: Context): PeopleServices =
            instance ?: synchronized(this) {
                instance ?: PeopleServices(context).also { instance = it }
            }
    }
}
