package kr.youngil.acousticvision.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 전체 데이터 백업(ZIP) 내보내기·복원 (V3: 업데이트 전 내보내기, 재설치 후 복원).
 * - 포함: projects/, calibration/, diagnostics/
 * - manifest.json: 형식, 앱 버전, 파일별 크기·SHA-256
 * - 복원: 경로 검증(zip-slip 차단), SHA-256 검증, 기존 파일은 덮어쓰지 않음(원본 보존)
 */
object Backup {
    const val FORMAT = "youngil-acoustic-vision-backup/1"
    const val MANIFEST = "manifest.json"
    val INCLUDED_DIRS = listOf("projects", "calibration", "diagnostics")
    private const val MAX_TOTAL_BYTES = 2L * 1024 * 1024 * 1024 // 2 GB 상한 (비정상 파일 방어)

    data class ExportResult(val files: Int, val bytes: Long)
    data class ImportResult(val restored: Int, val skippedExisting: Int, val error: String? = null)

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { ins ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = ins.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun listFiles(root: File): List<File> = INCLUDED_DIRS.flatMap { d ->
        File(root, d).walkTopDown().filter { it.isFile && !it.name.endsWith(".tmp") }.toList()
    }.sortedBy { it.path }

    fun export(root: File, out: OutputStream, appVersion: String, createdAtEpochMs: Long): ExportResult {
        val files = listFiles(root)
        var bytes = 0L
        val entries = ArrayList<Triple<String, Long, String>>()
        ZipOutputStream(out).use { zip ->
            for (f in files) {
                val rel = f.relativeTo(root).invariantSeparatorsPath
                zip.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                bytes += f.length()
                entries += Triple(rel, f.length(), sha256(f))
            }
            val manifest = buildJsonObject {
                put("format", FORMAT)
                put("appVersion", appVersion)
                put("createdAtEpochMs", createdAtEpochMs)
                put("fileCount", entries.size)
                putJsonArray("files") {
                    for ((p, size, h) in entries) add(buildJsonObject { put("path", p); put("size", size); put("sha256", h) })
                }
            }
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(Json.encodeToString(JsonObject.serializer(), manifest).toByteArray())
            zip.closeEntry()
        }
        return ExportResult(files.size, bytes)
    }

    /** 경로가 허용 폴더 안의 상대경로인지 (절대경로·상위경로·숨은 우회 차단) */
    fun isSafePath(name: String): Boolean {
        if (name.isBlank() || name.startsWith("/") || name.startsWith("\\") || name.contains('\\') || name.contains(':')) return false
        val parts = name.split('/')
        if (parts.any { it == ".." || it == "." || it.isEmpty() }) return false
        return parts.size >= 2 && parts[0] in INCLUDED_DIRS
    }

    fun import(root: File, input: InputStream): ImportResult {
        val staging = File(root, ".restore-staging-${System.nanoTime()}")
        try {
            staging.mkdirs()
            var manifest: JsonObject? = null
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (e.name == MANIFEST) {
                        manifest = Json.parseToJsonElement(zip.readBytes().decodeToString()).jsonObject
                        continue
                    }
                    if (!isSafePath(e.name)) return ImportResult(0, 0, "허용되지 않은 경로: ${e.name}")
                    val target = File(staging, e.name)
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = zip.read(buf); if (n < 0) break
                            total += n
                            if (total > MAX_TOTAL_BYTES) return ImportResult(0, 0, "백업 크기가 허용 한도를 초과")
                            out.write(buf, 0, n)
                        }
                    }
                }
            }
            val m = manifest ?: return ImportResult(0, 0, "manifest.json 없음 — 이 앱의 백업 파일이 아님")
            if (m["format"]?.jsonPrimitive?.content != FORMAT) return ImportResult(0, 0, "지원하지 않는 백업 형식")
            val listed = m["files"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
            if (listed.size != (m["fileCount"]?.jsonPrimitive?.int ?: -1)) return ImportResult(0, 0, "manifest 파일 수 불일치")
            // 무결성 검증: 목록의 모든 파일이 있고 크기·해시가 같아야 함
            for (item in listed) {
                val path = item["path"]!!.jsonPrimitive.content
                if (!isSafePath(path)) return ImportResult(0, 0, "허용되지 않은 경로: $path")
                val f = File(staging, path)
                if (!f.exists()) return ImportResult(0, 0, "누락된 파일: $path")
                if (f.length() != item["size"]!!.jsonPrimitive.long || sha256(f) != item["sha256"]!!.jsonPrimitive.content) {
                    return ImportResult(0, 0, "손상된 파일(SHA-256 불일치): $path")
                }
            }
            val stagedCount = staging.walkTopDown().count { it.isFile }
            if (stagedCount != listed.size) return ImportResult(0, 0, "manifest에 없는 파일 포함")
            var restored = 0
            var skipped = 0
            for (item in listed) {
                val path = item["path"]!!.jsonPrimitive.content
                val dest = File(root, path)
                if (dest.exists()) { skipped++; continue } // 기존 데이터는 덮어쓰지 않음
                dest.parentFile?.mkdirs()
                File(staging, path).copyTo(dest)
                restored++
            }
            return ImportResult(restored, skipped)
        } catch (e: Exception) {
            return ImportResult(0, 0, "복원 실패: ${e.message}")
        } finally {
            staging.deleteRecursively()
        }
    }
}
