package io.legado.app.ci

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PersonalReleaseWorkflowTest {

    private val workflowText by lazy {
        findRepositoryFile(".github/workflows/personal-release.yml")
            .readText()
            .replace("\r\n", "\n")
    }

    private val buildText by lazy {
        findRepositoryFile("app/build.gradle")
            .readText()
            .replace("\r\n", "\n")
    }

    @Test
    fun `personal publication builds an upgrade-compatible release variant`() {
        assertTrue(workflowText.contains("./gradlew :app:assembleRelease"))
        assertTrue(workflowText.contains("-PpersonalReleaseApplicationIdSuffix=.debug"))
        assertTrue(workflowText.contains("app/build/outputs/apk/app/release"))
        assertTrue(workflowText.contains("name='com.legado.app.debug'"))
        assertTrue(workflowText.contains("versionName='${'$'}{VERSION}'"))
        assertFalse(workflowText.contains("versionName='${'$'}{VERSION}debug'"))
        assertTrue(workflowText.contains("Personal release APK must not be debuggable"))

        assertTrue(buildText.contains("project.findProperty(\"personalReleaseApplicationIdSuffix\")"))
        assertTrue(
            buildText.contains(
                "applicationIdSuffix personalReleaseApplicationIdSuffix ?: '.release'"
            )
        )
    }

    @Test
    fun `personal publication pins and verifies its signing identity`() {
        assertTrue(
            workflowText.contains(
                "EXPECTED_CERT_SHA256: " +
                    "9848d27c13c52394333ee88206e138c962f66b6229a5be83263e6c30c9b1ce11"
            )
        )
        assertTrue(workflowText.contains("RELEASE_STORE_FILE="))
        assertTrue(workflowText.contains("signing certificate: ${'$'}cert_sha"))
        assertTrue(workflowText.contains("构建类型：`release`"))
    }

    private fun findRepositoryFile(relativePath: String): File {
        val userDir = requireNotNull(System.getProperty("user.dir"))
        return generateSequence(File(userDir)) { it.parentFile }
            .map { File(it, relativePath) }
            .first { it.isFile }
    }
}
