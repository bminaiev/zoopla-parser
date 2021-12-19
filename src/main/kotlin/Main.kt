import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
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

fun buildPropertyLink(id: Int): String {
    return "https://www.zoopla.co.uk/to-rent/details/$id/"
}

fun convertOneProperty(propertyId: Int, config: Config): Property? {
    val link = buildPropertyLink(propertyId)
    val propertyHTML = Utils.sendQuery(link)
    if (propertyHTML == null) {
        Logger.println("Skip property, because can't download html")
        return null
    }
    val parsedHTML = Jsoup.parse(propertyHTML)
    val priceStr = parsedHTML.selectFirst(PRICE_CLASS).text()
    val pricePoundsPerMonth = RentCost(priceStr)
    val address = Address(parsedHTML.select(ADDRESS_CLASS).text())
    val nextData = parsedHTML.selectFirst(NEXT_DATA_CLASS).html()
    val nextDataJson = Json.parseJson(nextData)
    val curPropertyJson =
        nextDataJson.jsonObject.getObject("props").getObject("pageProps").getObject("listingDetails")
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
        areaSqM
    )
}

fun handleResponse(
    response: String,
    telegram: Telegram,
    config: Config,
    queryParams: ResultRow,
    users: List<ResultRow>,
    checkPropertiesEvenInDB: Boolean
) {
    val parsed = Jsoup.parse(response);
    val properties = parsed.select(LISTINGS_CLASS)

    val links = properties.map {
        it.attr("href")
    }

    Logger.println("total " + links.size + " properties!")


    val allProperties = links.mapNotNull { linkIt ->
        val propertyId = getIdFromLink(linkIt)
        transaction {
            val inDB = skipped_properties.select { skipped_properties.id eq propertyId }.toList()
            if (inDB.isEmpty() || checkPropertiesEvenInDB) {
                val result = try {
                    convertOneProperty(propertyId, config)
                } catch (e: Exception) {
                    Logger.println("Exception while checking property $propertyId: $e")
                    null
                }
                if (result == null) {
                    Logger.println("Will not check property $propertyId any more, save it to database")
                    skipped_properties.insertIgnore { it[id] = propertyId }
                }
                result
            } else {
                Logger.println("Skip checking $propertyId because already done it (based on database)\n")
                null
            }
        }

    }

    allProperties.forEach { property ->
        if (property.costPerMonth.pricePoundsPerMonth < (queryParams[QueryParamsTable.minPrice]
                ?: RentCost.DEFAULT_MIN_PRICE)
        ) {
            return@forEach
        }
        if (property.costPerMonth.pricePoundsPerMonth > (queryParams[QueryParamsTable.maxPrice]
                ?: RentCost.DEFAULT_MAX_PRICE)
        ) {
            return@forEach
        }
        val propertyId = property.id
        users.forEach { user ->
            val userName = user[UsersTable.name]
            val chatId = user[UsersTable.chatId]
            transaction {
                val inDB =
                    SeenPropertiesTable.select { (SeenPropertiesTable.id eq propertyId) and (SeenPropertiesTable.user_name eq userName) }
                        .toList()
                if (inDB.isEmpty()) {
                    Logger.println("Want to send $property to $chatId ($userName)")
                    telegram.sendProperty(property, chatId, queryParams[QueryParamsTable.tag])
                    SeenPropertiesTable.insert {
                        it[id] = propertyId
                        it[user_name] = userName
                    }
                } else {
                    Logger.println("Skip sending $propertyId because already done it (based on database)")
                }
            }
        }
    }
}

fun sendRequest(telegram: Telegram, config: Config, checkPropertiesEvenInDB: Boolean) {
    Database.connect(
        config.dbURL, driver = "org.postgresql.Driver",
        user = config.dbUser, password = config.dbPassword
    )

    Databases.reinitialize()

    val queryParams = transaction { QueryParamsTable.selectAll().toList() }

    val additionalParams =
        "&beds_max=2&page_size=100&include_shared_accommodation=false&price_frequency=per_month&results_sort=newest_listings&search_source=refine&added=24_hours"

    queryParams.forEach { queryParam ->
        val queryParamId = queryParam[QueryParamsTable.id]
        val users = transaction {
            (SubscriptionsTable innerJoin UsersTable).slice(UsersTable.name, UsersTable.chatId)
                .select { (SubscriptionsTable.queryParamsId eq queryParamId) and (SubscriptionsTable.userName eq UsersTable.name) }
                .toList()
        }

        Logger.println("users: ${users}, query : $queryParam")
        if (users.isEmpty()) {
            return@forEach
        }
        val url = BASE_ADDRESS + queryParam[QueryParamsTable.queryUrl] + additionalParams
        val response = Utils.sendQuery(url, useCache = false)!!
        handleResponse(response, telegram, config, queryParam, users, checkPropertiesEvenInDB)
    }
}

fun do_test(telegram: Telegram, config: Config) {
    val propertyId = 60395544
    val queryParams = QueryParams(
        "test-url",
        "test-tag"
    )
    val property = convertOneProperty(propertyId, config)!!
    Logger.println("converted!")
    Logger.println("Want to send $property\n")
    val chatId = config.users["Borys"]!!.chatId
    telegram.sendProperty(property, chatId, "test-tag")
    Logger.println("Finished sending")
}

@UnstableDefault
fun main(args: Array<String>) {
//    testParseProperty()
//    testmd5()
    val checkPropertiesEvenInDB = args.contains("-check-properties-in-db")

    Logger.println("check properties in db: $checkPropertiesEvenInDB")
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
        sendRequest(telegram, config, checkPropertiesEvenInDB)
        Logger.println("Check right move also!")
        // TODO: fix parsing here...
//        RightMove.getNewPropertiesAndSendUpdates(config, telegram)
    }
}