import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.URL
import java.security.MessageDigest

const val BASE_ADDRESS = "https://www.zoopla.co.uk";

const val CACHE_DIR = "responses-cache/"

const val LISTINGS_CLASS = ".listing-results-price"
const val PRICE_CLASS = ".ui-pricing__main-price"
const val FLOOR_CLASS = ".ui-modal-floorplan__wrap"
const val GALERY_CLASS = ".ui-modal-gallery__asset--center-content"
const val SUMMARY_CLASS = ".dp-sidebar-wrapper__summary"
const val ADDRESS_CLASS = ".ui-property-summary__address"

fun makeGoodFilename(filename: String): String {
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

fun getPhotosLink(id: Int) = "https://www.zoopla.co.uk/to-rent/details/photos/$id"

fun getIdFromLink(link: String): Int {
    return link.substring(link.lastIndexOf('/') + 1).toInt();
}

fun parseMonthPrice(s: String): Int? {
    if (s[0] != '£') {
        return null;
    }
    if (!s.endsWith("pcm")) {
        return null;
    }
    return s.substring(1).replace(",", "").split(' ').first().toInt()
}

object seen_properties : Table() {
    val id = integer("id")
}

fun buildPropertyLink(id: Int): String {
    return "https://www.zoopla.co.uk/to-rent/details/$id"
}

fun convertOneProperty(propertyId: Int, queryParams: QueryParams, config: Config): Property? {
    val link = buildPropertyLink(propertyId)
    val propertyHTML = sendQuery(link)
    if (propertyHTML == null) {
        Logger.println("Skip property, because can't download html")
        return null
    }
    val parsedHTML = Jsoup.parse(propertyHTML)
    val priceStr = parsedHTML.selectFirst(PRICE_CLASS).text()
    val pricePoundsPerMonth = RentCost(priceStr)
    if (pricePoundsPerMonth.isOutOfRange()) {
        Logger.println("Skip property because of price $pricePoundsPerMonth")
        return null
    }
    val address = Address(parsedHTML.select(SUMMARY_CLASS).select(ADDRESS_CLASS).text())
    val floor = parsedHTML.selectFirst(FLOOR_CLASS)?.selectFirst(GALERY_CLASS)?.attr("style")
    if (floor == null) {
        Logger.println("Skip property because no floor plan")
        return null
    }
    val regex = "background-image: url\\('(.*)'\\)".toRegex()
    val (floorPlanImage) = regex.find(floor)!!.destructured
    val areaSqM = FloorPlanOCR.loadImageAndParseSqM(config, URL(floorPlanImage))
    val photosPage = sendQuery(getPhotosLink(propertyId))
    if (photosPage == null) {
        Logger.println("Skip property because can't get photos")
        return null
    }
    if (FloorPlanOCR.tooSmallArea(areaSqM)) {
        Logger.println("Skip property because area is too small: $areaSqM")
        return null
    }
    val photos = Jsoup.parse(photosPage).getElementsByTag("img").filter {
        it.attr("style").isNotEmpty()
    }.map { it.attr("src") }
    return Property(
        link,
        photos.toTypedArray(),
        pricePoundsPerMonth,
        floorPlanImage,
        address,
        propertyId,
        queryParams,
        areaSqM
    )
}

fun handleResponse(response: String, telegram: Telegram, config: Config, queryParams: QueryParams) {
    val properties = Jsoup.parse(response).select(LISTINGS_CLASS)

    val links = properties.map {
        it.attr("href")
    }

    Logger.println("total " + links.size + " properties!")

    val allProperties = links.mapNotNull { linkIt ->
        val propertyId = getIdFromLink(linkIt)
        convertOneProperty(propertyId, queryParams, config)
    }

    Database.connect(
        config.dbURL, driver = "org.postgresql.Driver",
        user = config.dbUser, password = config.dbPassword
    )

    allProperties.forEach { property ->
        val propertyId = property.id
        transaction {
            val inDB = seen_properties.select { seen_properties.id eq propertyId }.toList()
            if (inDB.isEmpty()) {
                Logger.println("Want to send $property\n")
                telegram.sendProperty(property)
                seen_properties.insert { it[id] = propertyId }
            } else {
                Logger.println("Skip sending $propertyId because already done it (based on database)\n")
            }
        }
    }
}

fun sendRequest(telegram: Telegram, config: Config) {
    val additionalParams =
        "&beds_max=2&page_size=100&include_shared_accommodation=false&price_frequency=per_month&results_sort=newest_listings&search_source=refine&added=24_hours"
    val queryParamsNear = QueryParams(
        "$BASE_ADDRESS/to-rent/property/london/britton-street/ec1m-5ny/?q=ec1m%205ny&radius=1",
        "near Farringdon"
    )
    val queryParamsAngel = QueryParams(
        "$BASE_ADDRESS/to-rent/property/angel/?q=Angel%2C%20London&radius=1",
        "near Angel"
    )
    val queryParamsKingsCross = QueryParams(
        BASE_ADDRESS + "/to-rent/property/london/kings-cross/?q=Kings%20Cross%2C%20London&radius=1",
        "near Kings Cross"
    )
    val queryParamsFacebook = QueryParams(
        "$BASE_ADDRESS/to-rent/property/station/tube/tottenham-court-road/?q=Tottenham%20Court%20Road%20Station%2C%20London&radius=1",
        "near FB office"
    )
    val queryParamsHampstead = QueryParams(
        "$BASE_ADDRESS/to-rent/property/station/tube/hampstead/?q=Hampstead%20Station%2C%20London&radius=1",
        "hampstead"
    )
    val queryNearHydePark = QueryParams(
        "$BASE_ADDRESS/to-rent/property/station/tube/sloane-square/?q=Sloane%20Square%20Station%2C%20London&radius=1",
        "hyde park"
    )
    val allQueryParams =
        listOf(
            queryParamsNear,
            queryParamsAngel,
            queryParamsFacebook,
            queryParamsKingsCross,
            queryParamsHampstead,
            queryNearHydePark
        )
    allQueryParams.forEach {
        Logger.println("Handle query with tag = " + it.tag)
        val response = sendQuery(it.baseUrl + additionalParams, useCache = false)!!
        handleResponse(response, telegram, config, it)
    }
}

fun testmd5() {
    System.err.println(makeGoodFilename("hello.html"));
}

fun main(args: Array<String>) {
//    testParseProperty()
//    testmd5()
    val config = Config.parseFromFile(args[0])
    val telegram = Telegram(config.telegramAPIKey, config.telegramChatIds)
    Logger.println("Start!")
    sendRequest(telegram, config)
}