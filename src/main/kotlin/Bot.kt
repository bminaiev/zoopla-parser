import com.elbekD.bot.Bot

fun main(args: Array<String>) {
    val token = args[0]
    val myUserId = args[1].toInt()
    System.err.println("Hello from Bot! TOKEN = $token")

    val bot = Bot.createPolling(username = "londonrent", token = token)
    bot.onCommand("/start") { msg, _ ->
        System.err.println(msg)
        bot.sendMessage(msg.chat.id, "Hello World!")
    }
    bot.start()
    bot.sendMessage(myUserId, "Hi Borys!")
    val p1 = bot.mediaPhoto("https://lc.zoocdn.com/4e998af2a37f628304158b3946d0809f62d41855.jpg")
    val p2 = bot.mediaPhoto("https://lc.zoocdn.com/1eed2dbe7f7e716741d7686ecd23662c14bb9688.jpg")
    // max 10 images
    bot.sendMediaGroup(myUserId, listOf(p1, p2))
}