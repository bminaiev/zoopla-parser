import com.elbekD.bot.Bot
import com.elbekD.bot.http.TelegramApiError
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

const val TIME_TO_SLEEP = 60 * 1000L

class Telegram(token: String, val users: Map<String, User>) {
    val bot = Bot.createPolling(username = "londonrent", token = token)

    val MAX_PHOTOS = 9
    val DELIMITER_MESSAGE = "------------------------------"

    val directedByImg = "https://i.kym-cdn.com/entries/icons/original/000/027/525/robert.jpg"

    private fun joinImages(firstImage: String, other: Array<String>): Array<String> {
        val res = other.toMutableList()
        res.add(0, firstImage)
        return res.toTypedArray()
    }

    private fun <R> tryTelegramAPIWithRetries(foo: () -> CompletableFuture<R>) {
        for (it in 0..5) {
            try {
                val fooRes = foo()
                fooRes.join()
                return
            } catch (e: TelegramApiError) {
                Logger.println("Got Telegram API error (strange?) :( $e")
                Logger.println("Going to sleep (strange?) $TIME_TO_SLEEP ms");
                Thread.sleep(TIME_TO_SLEEP)
            } catch (e: CompletionException) {
                Logger.println("Got Telegram API error :( $e")
                if (e.message?.contains("Bad Request: wrong type of the web page content")!!) {
                    return
                }
                if (e.message?.contains("Bad Request: group send failed")!!) {
                    return
                }
                Logger.println("Going to sleep $TIME_TO_SLEEP ms");
                Thread.sleep(TIME_TO_SLEEP)
            }
        }
        throw AssertionError("Too much tries to send message")
    }

    fun sendProperty(property: Property) {
        val hasExtraPhotos = if (property.imgs.size > MAX_PHOTOS) "(more photos available)\n" else ""
        val message =
            property.costPerMonth.toString() + property.link + "\n" + property.address + property.searchTag +
                    FloorPlanOCR.convertToString(property.areaSqM) + hasExtraPhotos
        val imgs = (joinImages(
            property.floorPlanImage,
            property.imgs
        ).take(MAX_PHOTOS) + directedByImg).map { bot.mediaPhoto(it) }
        Logger.println("total imsgs: " + imgs.size + ": " + imgs)
        users.forEach { entry ->
            if (entry.value.tags.contains(property.searchTag.tag)) {
                Logger.println("Send property to " + entry.key)
                val chatId = entry.value.chatId
                tryTelegramAPIWithRetries { bot.sendMessage(chatId, DELIMITER_MESSAGE) }
                tryTelegramAPIWithRetries { bot.sendMediaGroup(chatId, imgs) }
                tryTelegramAPIWithRetries {
                    bot.sendMessage(chatId, message, parseMode = "html")
                }
            } else {
                Logger.println("Skip sending property to " + entry.key + " because of tag: " + property.searchTag.tag)
            }
        }
    }
}

fun main(args: Array<String>) {
    val token = args[0]
    val myUserId = args[1].toLong()
    System.err.println("Hello from Bot! TOKEN = $token")

    val bot = Bot.createPolling(username = "londonrent", token = token)
    bot.onCommand("/start") { msg, _ ->
        System.err.println(msg)
        bot.sendMessage(msg.chat.id, "Hello World!")
    }
    bot.start()
    bot.sendMessage(myUserId, "Hi Borys!")
//    val p1 = bot.mediaPhoto("https://lc.zoocdn.com/4e998af2a37f628304158b3946d0809f62d41855.jpg")
//    val p2 = bot.mediaPhoto("https://lc.zoocdn.com/1eed2dbe7f7e716741d7686ecd23662c14bb9688.jpg")
    // max 10 images
//    val future = bot.sendMediaGroup(myUserId, listOf(p1, p2))
//    future.join()
//    bot.stop()
}