import kotlinx.serialization.UnstableDefault
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jsoup.Jsoup
import java.net.URL

class RightMove {
    @UnstableDefault
    companion object {
        private const val BASE_URL = "https://www.rightmove.co.uk"

        private const val PROPERTY_CARD_CLASS = ".propertyCard-link"

        private fun getIdFromLink(link: String): Int {
            // Example: /property-to-rent/property-85423135.html  ---> 85423135
            val withHtml = link.substring(link.lastIndexOf('-') + 1)
            return withHtml.substringBefore(".html").toInt()
        }

        private fun getPropertyUrlById(id: Int): String {
            return "$BASE_URL/property-to-rent/property-$id.html"
        }

        data class DataModel(
            val id: Int,
            val floorPlanURL: String,
            val costPerMonth: RentCost,
            val address: Address,
            val images: Array<String>
        )

        @UnstableDefault
        private fun getDataModel(id: Int): DataModel? {
            val url = getPropertyUrlById(id)
            val html = Utils.sendQuery(url, useCache = true)
            val parsedHtml = Jsoup.parse(html)
            val javaScript = parsedHtml.selectFirst("script[type=text/javascript]")
            val data = javaScript.data().substringAfter("window.PAGE_MODEL = ")
            val dataJson = Json.parseJson(data)
            val propertyData = dataJson.jsonObject.getObject("propertyData")

            val floorPlans = propertyData.getArray("floorplans")
            if (floorPlans.isEmpty()) {
                return null
            }
            val firstFloorPlan = floorPlans[0]
            val floorPlanURL = firstFloorPlan.jsonObject.getPrimitive("url").content

            val priceStr = propertyData.getObject("prices").getPrimitive("primaryPrice").content
            val costPerMonth = RentCost(priceStr)

            val addressStr = propertyData.getObject("address").getPrimitive("displayAddress").content
            val address = Address(addressStr)

            val images =
                propertyData.getArray("images").map { it.jsonObject.getPrimitive("url").content }.toTypedArray()

            return DataModel(id, floorPlanURL, costPerMonth, address, images)
        }

        private fun convertDataModelToProperty(dataModel: DataModel, searchTag: QueryParams, config: Config): Property {
            val link = getPropertyUrlById(dataModel.id)
            val areaSqM = FloorPlanOCR.loadImageAndParseSqM(config, URL(dataModel.floorPlanURL))
            return Property(
                link,
                dataModel.images,
                dataModel.costPerMonth,
                dataModel.floorPlanURL,
                dataModel.address,
                dataModel.id,
                searchTag,
                areaSqM
            )
        }

        @UnstableDefault
        private fun parseOneProperty(id: Int, config: Config): Property? {
            val dataModel = getDataModel(id)

            return dataModel?.let {
                convertDataModelToProperty(
                    it,
                    QueryParams("???", "rightmove: kings kross"),
                    config
                )
            }
        }

        @UnstableDefault
        fun getNewPropertiesList(config: Config): List<Property> {
            val url =
                "$BASE_URL/property-to-rent/find.html?searchType=RENT&locationIdentifier=REGION%5E87399&insId=1&radius=0.0&minPrice=&maxPrice=3500&minBedrooms=&maxBedrooms=&displayPropertyType=&maxDaysSinceAdded=1&sortByPriceDescending=&_includeLetAgreed=on&primaryDisplayPropertyType=&secondaryDisplayPropertyType=&oldDisplayPropertyType=&oldPrimaryDisplayPropertyType=&letType=&letFurnishType=&houseFlatShare="
            val response = Utils.sendQuery(url, useCache = false)

            val properties = Jsoup.parse(response).select(PROPERTY_CARD_CLASS)

            val links = properties.map {
                it.attr("href")
            }.filter { it.isNotEmpty() }.map { getIdFromLink(it) }

            Logger.println("total " + links.size + " properties!")

            return links.mapNotNull { parseOneProperty(it, config) }
        }

        private object seen_properties_right_move : Table() {
            val id = integer("id")
        }

        fun sendPropertiesWithDBCheck(properties: List<Property>, config: Config, telegram: Telegram) {
            System.err.println("Going to send (or skip) ${properties.size} properties!")

            Database.connect(
                config.dbURL, driver = "org.postgresql.Driver",
                user = config.dbUser, password = config.dbPassword
            )

            properties.forEach { property ->
                val propertyId = property.id
                transaction {
                    val inDB =
                        seen_properties_right_move.select { seen_properties_right_move.id eq propertyId }.toList()
                    if (inDB.isEmpty()) {
                        Logger.println("Want to send $property\n")
                        telegram.sendProperty(property)
                        seen_properties_right_move.insert { it[id] = propertyId }
                    } else {
                        Logger.println("Skip sending $propertyId because already done it (based on database)\n")
                    }
                }
            }
        }

        fun getNewPropertiesAndSendUpdates(config: Config, telegram: Telegram) {
            val properties = getNewPropertiesList(config).mapNotNull {
                val reasonToSkip = Property.existReasonToSkip(it)
                if (reasonToSkip != null) {
                    Logger.println(reasonToSkip)
                    null
                } else {
                    it
                }
            }
            sendPropertiesWithDBCheck(properties, config, telegram)
        }
    }
}

@UnstableDefault
fun main(args: Array<String>) {
    val config = Config.parseFromFile(args[0])
    val telegram = Telegram(config.telegramAPIKey, config.telegramChatIds)
    RightMove.getNewPropertiesAndSendUpdates(config, telegram)
//    handleOneProperty(85423135, config)
//    parseOneProperty(98025995, config)
    // TODO: Unit test later
//    val id = getIdFromLink("/property-to-rent/property-85423135.html")
//    System.err.println("id = $id")
}