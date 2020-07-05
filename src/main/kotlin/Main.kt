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

fun handleResponse(response: String, telegram: Telegram) {
    val properties = Jsoup.parse(response).select(LISTINGS_CLASS)

    val links = properties.map {
        it.attr("href")
    }

    println("total " + links.size + " properties!")

    val allProperies = ArrayList<Property>()

    links.forEach { linkIt ->
        val link = buildLink(linkIt)
        System.err.println("Send query $link")
        val propertyId = getIdFromLink(link)
        val propertyHTML = sendQuery(link)
        val parsedHTML = Jsoup.parse(propertyHTML)
        val priceStr = parsedHTML.selectFirst(PRICE_CLASS).text()
        val pricePoundsPerMonth = parseMonthPrice(priceStr) ?: 0
        val address = parsedHTML.select(SUMMARY_CLASS).select(ADDRESS_CLASS).text()
        System.err.println("address: $address")
        val floor = parsedHTML.selectFirst(FLOOR_CLASS)?.selectFirst(GALERY_CLASS)?.attr("style")
        val regex = "background-image: url\\('(.*)'\\)".toRegex()
        if (floor != null) {
            val (floorPlanImage) = regex.find(floor)!!.destructured
            System.err.println("price (pounds / month) = $pricePoundsPerMonth")

            val photosPage = sendQuery(getPhotosLink(propertyId))
            if (photosPage != null) {
                val photos = Jsoup.parse(photosPage).getElementsByTag("img").filter {
                    !it.attr("style").isEmpty()
                }.map { it.attr("src") }
                val property = Property(link, photos.toTypedArray(), pricePoundsPerMonth, floorPlanImage, address)
                allProperies.add(property)
            }
        }
    }

    println(allProperies.size)

    for (i in 0..4) {
        System.err.println("SEND PROPERTY?")
        telegram.sendProperty(allProperies[i])
        System.err.println("FINISH!")
    }
}

fun sendRequest(telegram: Telegram) {
    val url =
        BASE_ADDRESS + "/to-rent/property/london/britton-street/ec1m-5ny/?added=24_hours&include_shared_accommodation=false&price_frequency=per_month&q=ec1m%205ny&radius=1&results_sort=newest_listings&search_source=home&page_size=100"
    val response = sendQuery(url)!!
    handleResponse(response, telegram)
}

fun main(args: Array<String>) {
    val telegram = Telegram(args[0], args[1].toInt())
    println("Start!")
    sendRequest(telegram)
}