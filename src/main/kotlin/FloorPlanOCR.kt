import org.bytedeco.javacpp.lept.*
import org.bytedeco.javacpp.tesseract.TessBaseAPI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

class FloorPlanOCR {
    companion object {

        fun getPosOfMatched(s: String, pattern: String): Int {
            val sLowerCased = s.toLowerCase()
            val patternLowerCased = pattern.toLowerCase()
            var iter = 0
            for (c in patternLowerCased.toCharArray()) {
                while (iter != sLowerCased.length && sLowerCased[iter] != c) {
                    iter++
                }
                if (iter == sLowerCased.length) {
                    return Integer.MAX_VALUE
                }
                iter++
            }
            return iter
        }

        fun isDoublePart(c: Char): Boolean {
            return c == '.' || (c in '0'..'9')
        }

        fun extractDouble(s: String): Double? {
            var iter = 0
            while (iter != s.length && isDoublePart(s[iter])) {
                iter++
            }
            return s.substring(0, iter).toDoubleOrNull()
        }

        fun handleParsedText(text: String): Double? {
            var textIter = 0
            val allSqMeters = ArrayList<Double>()
            val allSqFeets = ArrayList<Double>()
            while (textIter < text.length) {
                if (text[textIter] in '0'..'9') {
                    val analyze = text.substring(textIter, Math.min(text.length, textIter + 15))
                    val sqFtPos = getPosOfMatched(analyze, "sqft")
                    val sqmPos = getPosOfMatched(analyze, "sqm")
                    val value = extractDouble(analyze)
                    if (sqmPos < sqFtPos && value != null) {
                        allSqMeters.add(value)
                        textIter += sqmPos
                        continue
                    }
                    if (sqFtPos < sqmPos && value != null) {
                        allSqFeets.add(value)
                        textIter += sqFtPos
                        continue
                    }
                }
                textIter++
            }
            allSqMeters.sort()
            if (allSqMeters.isNotEmpty()) {
                return allSqMeters.last()
            }
            allSqFeets.sort()
            if (allSqFeets.isNotEmpty()) {
                return allSqFeets.last() / 10.7639
            }
            return null
        }

        fun parseOneImage(api: TessBaseAPI, name: String): Double? {
            val image: PIX = pixRead(name)
            api.SetImage(image)

            val outText = api.GetUTF8Text()
//    File(name + ".txt").writeText(outText.string)
            val res = handleParsedText(outText.string)
            outText.deallocate()
            pixDestroy(image)
            return res
        }

        fun loadImageAndParseSqM(config: Config, url: URL): Double? {
            val tmpFile = "image.jpeg"
            Files.copy(url.openStream(), Paths.get(tmpFile), StandardCopyOption.REPLACE_EXISTING)


            val api = TessBaseAPI()
            if (api.Init(config.tesseractPathData, "eng") != 0) {
                System.err.println("Could not initialize tesseract.")
                exitProcess(1)
            }

            val sqm = parseOneImage(api, tmpFile)

            api.End()

            return sqm
        }

        fun toString(area: Double?): String {
            if (area == null) {
                return "???"
            } else {
                return String.format("%.3f sq. m.", area)
            }
        }

        fun convertToString(area: Double?): String {
            val res = toString(area)
            return "area: $res\n"
        }
    }


}

fun main(args: Array<String>) {
//    loadImageAndParse(URL("https://lc.zoocdn.com/98ee31d14914d9721f2065543998d910caa3cf82.jpg"))
//    println("Hello, world!")
//    val api = TessBaseAPI()
//    if (api.Init("/Users/bminaiev/Downloads/tessdata_best-master/", "eng") != 0) {
//        System.err.println("Could not initialize tesseract.")
//        exitProcess(1)
//    }
//    File("ocr-test").walk().forEach { file ->
//        if (file.isDirectory) {
//            return@forEach
//        }
//        if (file.absolutePath.endsWith(".txt")) {
//            println(file.absolutePath)
//            parseOneText(file)
//            return@forEach
//        }
//        if (file.absolutePath.endsWith(".jpeg")) {
////            println(file.absolutePath)
////            parseOneImage(api, file.absolutePath)
//        }
//    }
//    api.End()
}