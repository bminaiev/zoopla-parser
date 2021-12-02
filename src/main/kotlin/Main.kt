import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jsoup.Jsoup

import java.net.URL

const val BASE_ADDRESS = "https://www.zoopla.co.uk";
const val BASE_CDN = "https://lc.zoocdn.com/"

private const val LISTINGS_CLASS = "a[data-testid=listing-details-link]"
private const val PRICE_CLASS = "span[data-testid=price]"
private const val ADDRESS_CLASS = "span[data-testid=address-label]"
private const val NEXT_DATA_CLASS = "script[id=__NEXT_DATA__]"

fun getPhotosLink(id: Int) = "https://www.zoopla.co.uk/to-rent/details/photos/$id"

private fun getIdFromLink(link: String): Int {
    val link = link.substring(0, link.length - 1);
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

private object seen_properties : Table() {
    val id = integer("id")
}

fun buildPropertyLink(id: Int): String {
    return "https://www.zoopla.co.uk/to-rent/details/$id/"
}

fun convertOneProperty(propertyId: Int, queryParams: QueryParams, config: Config): Property? {
    val link = buildPropertyLink(propertyId)
    val propertyHTML = Utils.sendQuery(link)
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
    val address = Address(parsedHTML.select(ADDRESS_CLASS).text())
    val nextData = parsedHTML.selectFirst(NEXT_DATA_CLASS).html()
    val nextDataJson = Json.parseJson(nextData)
    val curPropertyJson =
        nextDataJson.jsonObject.getObject("props").getObject("pageProps").getObject("data").getObject("listing")
    val floorPlan = curPropertyJson.getObject("floorPlan")

    val image = floorPlan["image"]
    if (image == null || image.isNull || image.jsonArray.size == 0) {
        Logger.println("Skip property because no floor plan")

        return null;
    }

    val images = image.jsonArray
    val imageFilename = images[0].jsonObject.getPrimitive("filename").content
    val floorPlanImage = BASE_CDN + imageFilename

    val areaSqM = FloorPlanOCR.loadImageAndParseSqM(config, URL(floorPlanImage))
    val photosPage = Utils.sendQuery(getPhotosLink(propertyId))
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
    val parsed = Jsoup.parse(response);
    val properties = parsed.select(LISTINGS_CLASS)

    val links = properties.map {
        it.attr("href")
    }

    Logger.println("total " + links.size + " properties!")

    val allProperties = links.mapNotNull { linkIt ->
        val propertyId = getIdFromLink(linkIt)
        try {
            convertOneProperty(propertyId, queryParams, config)
        } catch (e: Exception) {
            null
        }
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
    val queryParamsFarringdon = QueryParams(
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
    val queryNearCanaryWharf = QueryParams(
        "$BASE_ADDRESS/to-rent/property/station/tube/canary-wharf/?q=Canary%20Wharf%20Station%2C%20London&radius=1",
        "canary wharf"
    )
    val queryNearNottingHill = QueryParams(
        "$BASE_ADDRESS/to-rent/property/notting-hill/?q=Notting%20Hill&radius=1",
        "notting hill"
    )
    val queryNearLiverpoolStreet = QueryParams(
        "$BASE_ADDRESS/to-rent/property/liverpool-street/?q=Liverpool%20Street%2C%20London&radius=1",
        "Liverpool Street"
    )
    val queryNearTowerBridge = QueryParams(
        "$BASE_ADDRESS/to-rent/property/tower-bridge/?q=Tower%20Bridge%2C%20London&radius=1",
        "Tower Bridge"
    )
    val queryNearVauxhall = QueryParams(
        "$BASE_ADDRESS/to-rent/property/london/vauxhall/?q=Vauxhall%2C%20London&radius=1",
        "Vauxhall"
    )
    val allQueryParams =
        listOf(
            queryParamsFarringdon,
            queryParamsAngel,
            queryParamsKingsCross,
            queryParamsHampstead,
            queryNearHydePark,
//            queryNearCanaryWharf,
            queryNearNottingHill,
            queryNearLiverpoolStreet,
            queryNearTowerBridge,
            queryNearVauxhall
        )
    allQueryParams.forEach {
        Logger.println("Handle query with tag = " + it.tag)
        val response = Utils.sendQuery(it.baseUrl + additionalParams, useCache = false)!!
        handleResponse(response, telegram, config, it)
    }
}

fun do_test(telegram: Telegram, config: Config) {
    val propertyId = 60291257
    val queryParams = QueryParams(
        "test-url",
        "test-tag"
    )
    val property = convertOneProperty(propertyId, queryParams, config)!!
    Logger.println("converted!")
    Logger.println("Want to send $property\n")
    telegram.sendProperty(property)
    Logger.println("Finished sending")
}

@UnstableDefault
fun main(args: Array<String>) {
//    testParseProperty()
//    testmd5()
    Logger.println(args.contentToString())
    if (args[0].equals("test")) {
        Logger.println("Testing env!")
        val config = Config.parseFromFile(args[1])
        val telegram = Telegram(config.telegramAPIKey, config.users)
        do_test(telegram, config)
//        RightMove.getNewPropertiesAndSendUpdates(config, telegram)
    } else {
        val config = Config.parseFromFile(args[0])
        val telegram = Telegram(config.telegramAPIKey, config.users)
        Logger.println("Start!")
        sendRequest(telegram, config)
        Logger.println("Check right move also!")
        // TODO: fix parsing here...
//        RightMove.getNewPropertiesAndSendUpdates(config, telegram)
    }
}