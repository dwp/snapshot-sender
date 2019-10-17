package app.batch

import app.domain.DecryptedStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.springframework.batch.item.ItemWriter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader

@Component
@Profile("consoleWriter")
class ConsoleWriter : ItemWriter<DecryptedStream> {
    override fun write(items: MutableList<out DecryptedStream>) {
        items.forEach { item ->
            var inputStream = item.inputStream
            if (item.fileName.contains("bz2")) {
                inputStream = CompressorStreamFactory().createCompressorInputStream(
                    CompressorStreamFactory.BZIP2,
                    item.inputStream)
            }
            BufferedReader(InputStreamReader(inputStream)).forEachLine {
                println(it)
            }
            println()
        }
    }
}
