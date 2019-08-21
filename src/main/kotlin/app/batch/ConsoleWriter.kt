package app.batch

import app.domain.DecryptedStream
import org.springframework.batch.item.ItemWriter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader

@Component
@Profile("consoleWriter")
class ConsoleWriter: ItemWriter<DecryptedStream> {
    override fun write(items: MutableList<out DecryptedStream>) {
        items.forEach { item ->
            BufferedReader(InputStreamReader(item.inputStream)).forEachLine {
                println(it)
            }
            println()
        }
    }
}