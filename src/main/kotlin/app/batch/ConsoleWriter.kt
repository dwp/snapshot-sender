package app.batch

import org.springframework.batch.item.ItemWriter
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

@Component
@Profile("consoleWriter")
class ConsoleWriter: ItemWriter<InputStream> {
    override fun write(items: MutableList<out InputStream>) {
        items.forEach { item ->
            BufferedReader(InputStreamReader(item)).forEachLine {
                println(it)
            }
            println()
        }
    }
}