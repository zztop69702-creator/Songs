package kr.youngil.acousticvision

import kr.youngil.acousticvision.data.Backup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 백업·복원 (순수 JVM) */
class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun seed(root: File) {
        File(root, "projects/p1/measurements").mkdirs()
        File(root, "projects/p1/project.json").writeText("""{"id":"p1"}""")
        File(root, "projects/p1/measurements/m1.json").writeText("""{"id":"m1"}""")
        File(root, "projects/p1/measurements/m1.jpg").writeBytes(ByteArray(5000) { it.toByte() })
        File(root, "calibration/profiles").mkdirs()
        File(root, "calibration/profiles/cp1.json").writeText("""{"profileId":"cp1"}""")
        File(root, "projects/p1/project.json.tmp").writeText("임시") // 제외 대상
        File(root, "other").mkdirs()
        File(root, "other/secret.txt").writeText("포함되면 안 됨")
    }

    private fun exportBytes(root: File): ByteArray = ByteArrayOutputStream().also { Backup.export(root, it, "test", 1L) }.toByteArray()

    @Test fun exportThenRestoreIntoEmptyAppData() {
        val src = tmp.newFolder("src"); seed(src)
        val zip = exportBytes(src)
        val names = ZipInputStream(ByteArrayInputStream(zip)).use { z -> generateSequence { z.nextEntry }.map { it.name }.toList() }
        assertTrue("manifest.json" in names)
        assertFalse(names.any { it.startsWith("other/") || it.endsWith(".tmp") })

        val dst = tmp.newFolder("dst")
        val r = Backup.import(dst, ByteArrayInputStream(zip))
        assertNull(r.error)
        assertEquals(4, r.restored)
        assertEquals(File(src, "projects/p1/measurements/m1.jpg").readBytes().toList(), File(dst, "projects/p1/measurements/m1.jpg").readBytes().toList())
        assertFalse("임시 복원 폴더 정리", dst.listFiles()!!.any { it.name.startsWith(".restore-staging") })
    }

    @Test fun restoreNeverOverwritesExistingData() {
        val src = tmp.newFolder("src"); seed(src)
        val zip = exportBytes(src)
        val dst = tmp.newFolder("dst")
        File(dst, "projects/p1").mkdirs()
        File(dst, "projects/p1/project.json").writeText("현재 데이터")
        val r = Backup.import(dst, ByteArrayInputStream(zip))
        assertEquals(1, r.skippedExisting)
        assertEquals(3, r.restored)
        assertEquals("현재 데이터", File(dst, "projects/p1/project.json").readText())
    }

    @Test fun zipSlipPathIsRejected() {
        val bad = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("projects/../../evil.txt")); z.write("x".toByteArray()); z.closeEntry() }
        }.toByteArray()
        val dst = tmp.newFolder("dst")
        val r = Backup.import(dst, ByteArrayInputStream(bad))
        assertNotNull(r.error)
        assertFalse(File(tmp.root, "evil.txt").exists())
        assertFalse(Backup.isSafePath("/projects/a"))
        assertFalse(Backup.isSafePath("other/a"))
        assertTrue(Backup.isSafePath("projects/p1/project.json"))
    }

    @Test fun tamperedFileIsRejectedAndNothingRestored() {
        val src = tmp.newFolder("src"); seed(src)
        val zip = exportBytes(src)
        // m1.json 내용 변조
        val tampered = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zo ->
                ZipInputStream(ByteArrayInputStream(zip)).use { zi ->
                    while (true) {
                        val e = zi.nextEntry ?: break
                        val data = zi.readBytes()
                        zo.putNextEntry(ZipEntry(e.name))
                        zo.write(if (e.name.endsWith("m1.json")) """{"id":"HACK"}""".toByteArray() else data)
                        zo.closeEntry()
                    }
                }
            }
        }.toByteArray()
        val dst = tmp.newFolder("dst")
        val r = Backup.import(dst, ByteArrayInputStream(tampered))
        assertTrue(r.error!!.contains("SHA-256"))
        assertEquals(0, r.restored)
        assertFalse(File(dst, "projects").exists())
    }

    @Test fun zipWithoutManifestIsRejected() {
        val noManifest = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("projects/p/project.json")); z.write("{}".toByteArray()); z.closeEntry() }
        }.toByteArray()
        val r = Backup.import(tmp.newFolder("dst"), ByteArrayInputStream(noManifest))
        assertTrue(r.error!!.contains("manifest"))
    }
}
