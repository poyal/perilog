package com.poyal.perilog.backup

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.poyal.perilog.PerilogApplication
import com.poyal.perilog.data.*
import com.poyal.perilog.domain.validate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.util.concurrent.TimeUnit

val Context.deviceStore by preferencesDataStore("device")
object DeviceKeys {
    val folder=stringPreferencesKey("folder")
    val lastBackup=longPreferencesKey("lastBackup")
    val status=stringPreferencesKey("backupStatus")
}

class BackupManager(private val context: Context, private val repository: Repository) {
    private val mutex=Mutex()
    suspend fun read(uri: Uri): Snapshot = withContext(Dispatchers.IO) {
        val bytes=context.contentResolver.openInputStream(uri)?.use { input ->
            val output=java.io.ByteArrayOutputStream()
            val buffer=ByteArray(8192)
            while(true) { val n=input.read(buffer);if(n<0)break;require(output.size()+n<=50*1024*1024){"50MB 이하의 백업을 선택해 주세요."};output.write(buffer,0,n) }
            output.toByteArray()
        } ?: error("파일을 열 수 없습니다.")
        require(bytes.size <= 50*1024*1024) { "50MB 이하의 백업을 선택해 주세요." }
        codec.decodeFromString<Snapshot>(bytes.toString(Charsets.UTF_8)).also(::validate)
    }
    suspend fun export(uri: Uri) = withContext(Dispatchers.IO) { write(uri,repository.snapshot()) }
    private suspend fun write(uri: Uri,s: Snapshot) {
        val json=codec.encodeToString(s)
        context.contentResolver.openOutputStream(uri,"wt")?.use { it.write(json.toByteArray()) } ?: error("파일을 쓸 수 없습니다.")
        require(read(uri)==s) { "저장한 백업을 확인하지 못했습니다. 기존 파일을 유지합니다." }
    }
    suspend fun protect(): File = withContext(Dispatchers.IO) {
        val s=repository.snapshot()
        val folder=File(context.filesDir,"protection").apply{mkdirs()}
        val file=File(folder,"before-restore-${System.currentTimeMillis()}.json")
        file.writeText(codec.encodeToString(s))
        require(codec.decodeFromString<Snapshot>(file.readText())==s)
        file
    }
    suspend fun restore(s: Snapshot) = mutex.withLock { validate(s); protect(); repository.restore(s) }
    suspend fun automatic(force: Boolean=false) = mutex.withLock {
        withContext(Dispatchers.IO) {
            val device=context.deviceStore.data.first()
            val uri=device[DeviceKeys.folder] ?: return@withContext
            val s=repository.snapshot()
            if(!force && System.currentTimeMillis()-(device[DeviceKeys.lastBackup] ?: 0L) < TimeUnit.DAYS.toMillis(s.preferences.backupDays.toLong())) return@withContext
            try {
                val folder=DocumentFile.fromTreeUri(context,Uri.parse(uri)) ?: error("백업 폴더를 다시 선택해 주세요.")
                require(folder.canWrite()) { "백업 폴더에 접근할 수 없습니다. 다시 선택해 주세요." }
                val name="perilog-auto-${System.currentTimeMillis()}.json"
                val output=folder.createFile("application/json",name) ?: error("백업 파일 생성 실패")
                try { write(output.uri,s) } catch(e: Exception) { output.delete(); throw e }
                context.deviceStore.edit { it[DeviceKeys.lastBackup]=System.currentTimeMillis(); it[DeviceKeys.status]="자동 백업 완료" }
                folder.listFiles().filter{it.name?.matches(Regex("perilog-auto-\\d+\\.json"))==true}
                    .sortedByDescending{it.name}.drop(s.preferences.keepBackups).forEach{it.delete()}
            } catch(e: Exception) {
                context.deviceStore.edit{it[DeviceKeys.status]="백업 실패: ${e.message ?: "폴더와 저장 공간을 확인해 주세요."}"}
                throw e
            }
        }
    }
    suspend fun chooseFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        context.deviceStore.edit{it[DeviceKeys.folder]=uri.toString(); it[DeviceKeys.lastBackup]=0}
        schedule(context)
    }
    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("perilog-backup",ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BackupWorker>(24,TimeUnit.HOURS).build())
        }
    }
}

class BackupWorker(context: Context,parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result = try {
        (applicationContext as PerilogApplication).backup.automatic(); Result.success()
    } catch(_: Exception) { Result.retry() }
}
