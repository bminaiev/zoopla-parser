import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
import java.io.File

@Serializable
data class Config(
    val telegramUserId: Int,
    val telegramAPIKey: String,
    val dbURL: String,
    val dbUser: String,
    val dbPassword: String
) {
    companion object {
        fun parseFromFile(filename: String): Config {
            val json = Json(JsonConfiguration.Stable)
            return json.parse(Config.serializer(), File(filename).readText())
        }
    }
}



