package cash.p.terminal.domain.usecase

import cash.p.terminal.core.tryOrNull
import io.horizontalsystems.core.CoreApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

class GetLocalizedAssetUseCase {

    companion object {
        const val CHANGELOG_PREFIX = "changelog_"
        const val ABOUT_PREMIUM_PREFIX = "about_premium_"
        const val ABOUT_PREMIUM_FULL_PREFIX = "about_premium_full_"

        const val RESOURCE_FOLDER = "common"
        const val FILE_SUFFIX = ".md"
        const val FALLBACK_LANGUAGE = "en"
    }

    suspend operator fun invoke(
        prefix: String,
        suffix: String = FILE_SUFFIX,
        fallbackLanguage: String = FALLBACK_LANGUAGE
    ): String = withContext(Dispatchers.IO) {
        val currentLanguage = getCurrentLanguage()
        readFileFromAssets(
            filePrefix = prefix,
            language = currentLanguage,
            fallbackLanguage = fallbackLanguage
        )
    }

    private fun readFileFromAssets(
        filePrefix: String,
        language: String,
        fallbackLanguage: String
    ): String {
        val availableFileList = getAvailableFiles(filePrefix)

        val primaryFile = findFile(
            availableFiles = availableFileList,
            filePrefix = filePrefix,
            language = language
        )
        if (primaryFile != null) {
            return tryOrNull { readFileFromAssets("$RESOURCE_FOLDER/$primaryFile") } ?: ""
        }

        if (language != fallbackLanguage) {
            val fallbackFile = findFile(availableFileList, filePrefix, fallbackLanguage)
            if (fallbackFile != null) {
                return tryOrNull { readFileFromAssets("$RESOURCE_FOLDER/$fallbackFile") } ?: ""
            }
        }

        return if (availableFileList.isNotEmpty()) {
            tryOrNull { readFileFromAssets("$RESOURCE_FOLDER/${availableFileList.first()}") } ?: ""
        } else {
            ""
        }
    }

    private fun getAvailableFiles(filePrefix: String): List<String> {
        return try {
            val commonFiles = CoreApp.instance.assets.list(RESOURCE_FOLDER) ?: emptyArray()

            commonFiles.filter { fileName ->
                val lowerFileName = fileName.lowercase()
                lowerFileName.startsWith(filePrefix) && lowerFileName.endsWith(FILE_SUFFIX)
            }.sortedBy { it.lowercase() }

        } catch (e: IOException) {
            emptyList()
        }
    }

    private fun findFile(
        availableFiles: List<String>,
        filePrefix: String,
        language: String
    ): String? {
        val targetPattern = "${filePrefix}${language}${FILE_SUFFIX}"

        return availableFiles.find { fileName ->
            fileName.lowercase() == targetPattern
        }
    }

    private fun readFileFromAssets(fileName: String): String {
        return CoreApp.instance.assets.open(fileName).use { inputStream ->
            inputStream.bufferedReader().use { reader ->
                reader.readText()
            }
        }
    }

    private fun getCurrentLanguage(): String {
        return Locale.getDefault().language
    }
}