package app.batch

import app.domain.EncryptedStream
import app.domain.EncryptionMetadata
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemReader
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileInputStream

@Component
@Profile("directoryReader")
class DirectoryReader : ItemReader<EncryptedStream> {

    @Synchronized
    override fun read(): EncryptedStream? {
        if (fileIndex < files().size) {
            val encryptedFile = files()[fileIndex++]
            val metadataFile: File = File(encryptedFile.parentFile,
                encryptedFile.name.replace(Regex("""\.\w{3}\.\w{3}(\.\w{3})?$"""), ".metadata"))
            logger.info("encryptedFile: '$encryptedFile', metadataFile: '$metadataFile'")
            var iv: String = ""
            var dataKeyEncryptionKey: String = ""
            var ciphertext: String = ""
            val entry = Regex("""(^\w+?)=(.*)""")
            metadataFile.forEachLine { line ->
                val match: MatchResult? = entry.matchEntire(line)
                if (match != null) {
                    val key = match.groupValues[1]
                    val value = match.groupValues[2]
                    when (key) {
                        "iv" -> iv = value
                        "ciphertext" -> ciphertext = value
                        "dataKeyEncryptionKeyId" -> dataKeyEncryptionKey = value
                    }

                }
            }
            val encryptionMetadata = EncryptionMetadata(iv, dataKeyEncryptionKey, ciphertext, "")
            return EncryptedStream(FileInputStream(encryptedFile), encryptedFile.name, encryptedFile.absolutePath, encryptionMetadata)
        }
        else {
            return null
        }
    }

    @Synchronized
    private fun files(): List<File> {
        if (_files.isEmpty()) {
            _files = File(inputDirectory).listFiles { pathname ->
                pathname.isFile && pathname.name.endsWith(".enc")
            }.toList()
        }
        return _files
    }

    private var _files: List<File> = ArrayList()
    private var fileIndex = 0

    @Value("\${input.directory}")
    private lateinit var inputDirectory: String

    companion object {
        val logger: Logger = LoggerFactory.getLogger(DirectoryReader::class.toString())
    }

}