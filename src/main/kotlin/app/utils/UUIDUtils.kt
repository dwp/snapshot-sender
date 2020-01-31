package app.utils

import org.springframework.stereotype.Component
import java.util.*

@Component
class UUIDGenerator {

    fun randomUUID(): String {
        return UUID.randomUUID().toString()
    }
}
