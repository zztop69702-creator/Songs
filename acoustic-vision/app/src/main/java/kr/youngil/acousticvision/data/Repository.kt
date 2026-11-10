package kr.youngil.acousticvision.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kr.youngil.acousticvision.core.calibration.CalibrationProfile
import kr.youngil.acousticvision.core.calibration.ProfileStatus
import kr.youngil.acousticvision.core.calibration.ProfileValidity
import kr.youngil.acousticvision.core.model.AppJson
import kr.youngil.acousticvision.core.model.AudioConfig
import kr.youngil.acousticvision.core.model.Calibration
import kr.youngil.acousticvision.core.model.Measurement
import kr.youngil.acousticvision.core.model.Project
import java.io.File

/**
 * 앱 전용 저장소(filesDir) 기반 파일 저장.
 * projects/{projectId}/project.json
 * projects/{projectId}/measurements/{id}.json, {id}.jpg
 * calibration/{deviceKey}.json
 * 원본 측정 JSON은 덮어쓰지 않는다(새 측정은 새 id).
 */
class Repository(context: Context) {
    private val root = context.applicationContext.filesDir
    private val projectsDir = File(root, "projects")
    private val calibrationDir = File(root, "calibration")
    val diagnosticsDir = File(root, "diagnostics")

    private fun projectDir(id: String) = File(projectsDir, safe(id))
    private fun measurementsDir(projectId: String) = File(projectDir(projectId), "measurements")

    suspend fun listProjects(): List<Project> = withContext(Dispatchers.IO) {
        projectsDir.listFiles()?.mapNotNull { dir ->
            runCatching { AppJson.decodeFromString(Project.serializer(), File(dir, "project.json").readText()) }.getOrNull()
        }?.sortedByDescending { it.createdAtEpochMs } ?: emptyList()
    }

    suspend fun loadProject(id: String): Project? = withContext(Dispatchers.IO) {
        runCatching { AppJson.decodeFromString(Project.serializer(), File(projectDir(id), "project.json").readText()) }.getOrNull()
    }

    suspend fun saveProject(p: Project) = withContext(Dispatchers.IO) {
        writeAtomic(File(projectDir(p.id), "project.json"), AppJson.encodeToString(Project.serializer(), p))
    }

    suspend fun listMeasurements(projectId: String): List<Measurement> = withContext(Dispatchers.IO) {
        measurementsDir(projectId).listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            runCatching { AppJson.decodeFromString(Measurement.serializer(), f.readText()) }.getOrNull()
        }?.sortedByDescending { it.createdAtEpochMs } ?: emptyList()
    }

    suspend fun loadMeasurement(projectId: String, id: String): Measurement? = withContext(Dispatchers.IO) {
        runCatching {
            AppJson.decodeFromString(Measurement.serializer(), File(measurementsDir(projectId), "${safe(id)}.json").readText())
        }.getOrNull()
    }

    fun measurementJsonFile(projectId: String, id: String) = File(measurementsDir(projectId), "${safe(id)}.json")

    suspend fun loadSnapshot(projectId: String, fileName: String): Bitmap? = withContext(Dispatchers.IO) {
        val f = File(measurementsDir(projectId), safe(fileName))
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
    }

    /** 스냅샷 이미지를 먼저 쓰고, 측정 JSON을 원자적으로 기록한다. */
    suspend fun saveMeasurement(m: Measurement, snapshot: Bitmap?) = withContext(Dispatchers.IO) {
        val dir = measurementsDir(m.projectId)
        dir.mkdirs()
        val snapName = m.snapshotFile
        if (snapshot != null && snapName != null) {
            File(dir, safe(snapName)).outputStream().use { snapshot.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        val target = File(dir, "${safe(m.id)}.json")
        check(!target.exists()) { "같은 id의 측정이 이미 있습니다 (원본 보존)" }
        writeAtomic(target, AppJson.encodeToString(Measurement.serializer(), m))
    }

    suspend fun loadCalibration(deviceKey: String): Calibration? = withContext(Dispatchers.IO) {
        runCatching {
            AppJson.decodeFromString(Calibration.serializer(), File(calibrationDir, "${safe(deviceKey)}.json").readText())
        }.getOrNull()
    }

    suspend fun saveCalibration(c: Calibration) = withContext(Dispatchers.IO) {
        writeAtomic(File(calibrationDir, "${safe(c.inputDeviceKey)}.json"), AppJson.encodeToString(Calibration.serializer(), c))
    }

    suspend fun clearCalibration(deviceKey: String) = withContext(Dispatchers.IO) {
        File(calibrationDir, "${safe(deviceKey)}.json").delete()
        Unit
    }

    // ---------- 스마트 비교보정 V2 프로파일 ----------
    private val profilesDir = File(calibrationDir, "profiles")
    /** 보정 증거(소음계 결과 화면 사진) 저장 폴더 */
    val evidenceDir = File(calibrationDir, "evidence")

    fun newEvidenceFile(name: String): File = File(evidenceDir, safe(name)).also { evidenceDir.mkdirs() }

    suspend fun saveProfile(p: CalibrationProfile) = withContext(Dispatchers.IO) {
        writeAtomic(File(profilesDir, "${safe(p.profileId)}.json"), AppJson.encodeToString(CalibrationProfile.serializer(), p))
    }

    suspend fun listProfiles(): List<CalibrationProfile> = withContext(Dispatchers.IO) {
        profilesDir.listFiles { f -> f.extension == "json" }?.mapNotNull { f ->
            runCatching { AppJson.decodeFromString(CalibrationProfile.serializer(), f.readText()) }.getOrNull()
        }?.sortedByDescending { it.createdAtEpochMs } ?: emptyList()
    }

    /** 현재 입력 조건에서 유효한 최신 ACTIVE 프로파일 (없으면 null) */
    suspend fun activeProfileFor(config: AudioConfig, buildFingerprint: String): CalibrationProfile? =
        listProfiles().firstOrNull { ProfileValidity.check(it, config, buildFingerprint).isEmpty() }

    /** 새 프로파일 활성화: 같은 입력 경로의 기존 ACTIVE 프로파일은 SUPERSEDED로 변경 (삭제하지 않음) */
    suspend fun activateProfile(p: CalibrationProfile, now: Long) {
        for (old in listProfiles()) {
            if (old.profileId != p.profileId && old.status == ProfileStatus.ACTIVE && old.inputDeviceKey == p.inputDeviceKey) {
                saveProfile(old.copy(status = ProfileStatus.SUPERSEDED, approvals = old.approvals + kr.youngil.acousticvision.core.calibration.ApprovalEvent(now, "SUPERSEDED", "새 프로파일 ${p.profileId} 활성화")))
            }
        }
        saveProfile(p)
    }

    suspend fun saveDiagnostics(name: String, json: String): File = withContext(Dispatchers.IO) {
        val f = File(diagnosticsDir, safe(name))
        writeAtomic(f, json)
        f
    }

    /** 앱 저장소 남은 공간 (바이트) */
    fun freeBytes(): Long = runCatching { root.usableSpace }.getOrDefault(Long.MAX_VALUE)

    private fun writeAtomic(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.delete()
            check(tmp.renameTo(target)) { "파일 저장 실패: ${target.name}" }
        }
    }

    companion object {
        fun safe(s: String): String = s.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
