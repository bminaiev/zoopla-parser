import java.time.Instant
import java.time.format.DateTimeFormatter

class Logger {
    companion object {
        fun println(message: String) {
            val time = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
            kotlin.io.println("$time: $message")
        }
    }
}