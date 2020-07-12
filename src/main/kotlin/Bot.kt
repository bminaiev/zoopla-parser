import com.elbekD.bot.Bot
import com.elbekD.bot.http.TelegramApiError

const val TIME_TO_SLEEP = 60 * 1000L

class Telegram(token: String, val chatIds: Array<Long>) {
    val bot = Bot.createPolling(username = "londonrent", token = token)

    val MAX_PHOTOS = 9
    val DELIMITER_MESSAGE = "------------------------------"

    val directedByImg = "https://i.kym-cdn.com/entries/icons/original/000/027/525/robert.jpg"

    private fun joinImages(firstImage: String, other: Array<String>): Array<String> {
        val res = other.toMutableList()
        res.add(0, firstImage)
        return res.toTypedArray()
    }

    private fun <R> tryTelegramAPIWithRetries(foo: () -> R): R {
        for (it in 0..10) {
            try {
                return foo()
            } catch (e: TelegramApiError) {
                Logger.println("Got Telegram API error :( $e")
                Logger.println("Going to sleep $TIME_TO_SLEEP ms");
                Thread.sleep(TIME_TO_SLEEP)
            }
        }
        throw AssertionError("Too much tries to send message")
    }

    fun sendProperty(property: Property) {
        val hasExtraPhotos = if (property.imgs.size > MAX_PHOTOS) "(more photos available)\n" else ""
        val message =
            property.costPerMonth.toString() + property.link + "\n" + property.address + property.searchTag + hasExtraPhotos
        val imgs = (joinImages(
            property.floorPlanImage,
            property.imgs
        ).take(MAX_PHOTOS) + directedByImg).map { bot.mediaPhoto(it) }
        Logger.println("total imsgs: " + imgs.size + ": " + imgs)
        chatIds.forEach { chatId ->
            tryTelegramAPIWithRetries { bot.sendMessage(chatId, DELIMITER_MESSAGE).join() }
            tryTelegramAPIWithRetries { bot.sendMediaGroup(chatId, imgs).join() }
            tryTelegramAPIWithRetries { bot.sendMessage(chatId, message, parseMode = "html").join() }
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