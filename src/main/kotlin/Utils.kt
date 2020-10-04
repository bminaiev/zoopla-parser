import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.URL
import java.security.MessageDigest

class Utils {
    companion object {
        private const val CACHE_DIR = "responses-cache/"

        private fun makeGoodFilename(filename: String): String {
            val bytes = (MessageDigest.getInstance("md5").digest(filename.toByteArray()))
            return BigInteger(1, bytes).toString(16).padStart(32, '0')
        }

        fun sendQuery(url: String, useCache: Boolean = true): String? {
            Logger.println("Send query $url, useCache = $useCache")
            val fileCache = File(CACHE_DIR + makeGoodFilename(url))
            if (useCache && fileCache.exists()) {
                val cachedRes = fileCache.readText()
                if (cachedRes.isEmpty()) {
                    return null;
                }
                return cachedRes;
            }
            return try {
                val response = URL(url).readText()
                fileCache.writeText(response)
                response
            } catch (e: IOException) {
                fileCache.writeText("");
                null;
            }
        }
    }
}