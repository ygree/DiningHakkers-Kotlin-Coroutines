package DiningHakkers

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor


class Hakker(val name: String)

sealed class ChopstickState
object ChopstickAvailable : ChopstickState()
class ChopstickTaken(val hakker: Hakker) : ChopstickState()

sealed class ChopstickCommand
class TakeChopstick(val hakker: Hakker, val replyTo: Channel<ChopstickAnswer>) : ChopstickCommand()
class PutChopstick(val hakker: Hakker) : ChopstickCommand()

sealed class ChopstickAnswer
data class ChopstickAnswerTaken(val name: String, val chopstick: SendChannel<ChopstickCommand>) : ChopstickAnswer()
class ChopstickBusy(name: String) : ChopstickAnswer()

fun CoroutineScope.chopstickActor(name: String) = actor<ChopstickCommand> {
    var state: ChopstickState = ChopstickAvailable
    for (msg in channel) {
        when (state) {
            // When a Chopstick is taken by a hakker
            // It will refuse to be taken by other hakkers
            // But the owning hakker can put it back
            is ChopstickTaken ->
                when (msg) {
                    is TakeChopstick -> msg.replyTo.send(ChopstickBusy(name))
                    is PutChopstick -> {
                        if (msg.hakker.name == state.hakker.name) {
                            state = ChopstickAvailable
                        } else {
                            TODO("Chopstick can't be put back by another hakker")
                        }
                    }
                    else -> TODO("Unexpected command")
                }
            // When a Chopstick is available, it can be taken by a hakker
            is ChopstickAvailable ->
                when (msg) {
                    is TakeChopstick -> {
                        state = ChopstickTaken(msg.hakker)
                        msg.replyTo.send(ChopstickAnswerTaken(name, channel))
                    }
                    else -> TODO("Chopstick isn't taken")
                }
        }
    }
}

fun main() = runBlocking {
    //    coroutineScope {
//        launch {
//            delay(1000L)
//            println(" world!")
//        }
//        print("Hello,")
//    }
//    println("Bye!")


//    readLine()
//
//    repeat(100_000) {
//        launch {
//            delay(1000L)
//            print(".")
//        }
//    }


//    val ch = Channel<Int>()
//
//    launch {
//        for (x in 1..5) {
//            ch.send(x)
//            delay(1000L)
//        }
//        ch.close()
//    }
//
//    for (i in ch) {
//        println(i)
//    }


    val reply = Channel<ChopstickAnswer>(3)

    val c1 = chopstickActor("chopstick-1")

    c1.send(TakeChopstick(Hakker("Yury"), reply))
    c1.send(TakeChopstick(Hakker("Andrey"), reply))
    c1.send(TakeChopstick(Hakker("Danil"), reply))

    for (r in reply) {
        println("reply: $r")
    }
}
