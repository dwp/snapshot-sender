package dwp.snapshot.sender

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SnapshotSenderApplication

fun main(args: Array<String>) {
	runApplication<SnapshotSenderApplication>(*args)
}
