import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jsoup.Jsoup
import java.io.File
import java.io.IOException
import java.net.URL

const val BASE_ADDRESS = "https://www.zoopla.co.uk";

const val CACHE_DIR = "responses-cache/"

const val LISTINGS_CLASS = ".listing-results-price"
const val PRICE_CLASS = ".ui-pricing__main-price"
const val FLOOR_CLASS = ".ui-modal-floorplan__wrap"
const val GALERY_CLASS = ".ui-modal-gallery__asset--center-content"
const val SUMMARY_CLASS = ".dp-sidebar-wrapper__summary"
const val ADDRESS_CLASS = ".ui-property-summary__address"

fun makeGoodFilename(filename: String): String {
    return filename.replace("[^a-zA-Z0-9.\\-]".toRegex(), "_")
}

fun sendQuery(url: String, useCache: Boolean = true): String? {
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

fun buildLink(relative: String) = BASE_ADDRESS + relative

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

fun handleResponse(response: String, telegram: Telegram, config: Config) {
    val properties = Jsoup.parse(response).select(LISTINGS_CLASS)

    val links = properties.map {
        it.attr("href")
    }

    Logger.println("total " + links.size + " properties!")

    val allProperies = ArrayList<Property>()

    links.forEach { linkIt ->
        val link = buildLink(linkIt)
        System.err.println("Send query $link")
        val propertyId = getIdFromLink(link)
        val propertyHTML = sendQuery(link)
        val parsedHTML = Jsoup.parse(propertyHTML)
        val priceStr = parsedHTML.selectFirst(PRICE_CLASS).text()
        val pricePoundsPerMonth = RentCost(priceStr)
        val address = Address(parsedHTML.select(SUMMARY_CLASS).select(ADDRESS_CLASS).text())
        val floor = parsedHTML.selectFirst(FLOOR_CLASS)?.selectFirst(GALERY_CLASS)?.attr("style")
        val regex = "background-image: url\\('(.*)'\\)".toRegex()
        if (floor != null) {
            val (floorPlanImage) = regex.find(floor)!!.destructured
            val photosPage = sendQuery(getPhotosLink(propertyId))
            if (photosPage != null) {
                val photos = Jsoup.parse(photosPage).getElementsByTag("img").filter {
                    !it.attr("style").isEmpty()
                }.map { it.attr("src") }
                val property =
                    Property(link, photos.toTypedArray(), pricePoundsPerMonth, floorPlanImage, address, propertyId)
                allProperies.add(property)
            }
        }
    }

    Database.connect(
        config.dbURL, driver = "org.postgresql.Driver",
        user = config.dbUser, password = config.dbPassword
    )

    allProperies.forEach { property ->
        val propertyId = property.id
        transaction {
            val inDB = seen_properties.select { seen_properties.id eq propertyId }.toList()
            if (inDB.isEmpty()) {
                Logger.println("Want to send $property\n")
                telegram.sendProperty(property)
                seen_properties.insert { it[id] = propertyId }
            } else {
                Logger.println("Skip sending $propertyId\n")
            }
        }
    }
}

fun sendRequest(telegram: Telegram, config: Config) {
    val url =
        BASE_ADDRESS + "/to-rent/property/london/britton-street/ec1m-5ny/?added=24_hours&include_shared_accommodation=false&price_frequency=per_month&q=ec1m%205ny&radius=1&results_sort=newest_listings&search_source=home&page_size=100"
    val response = sendQuery(url, useCache = false)!!
    handleResponse(response, telegram, config)
}

fun main(args: Array<String>) {
    val config = Config.parseFromFile(args[0])
    val telegram = Telegram(config.telegramAPIKey, config.telegramUserId)
    Logger.println("Start!")
    sendRequest(telegram, config)
}