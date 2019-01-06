package DiningHakkers

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

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



    val ch = Channel<Int>()

    launch {
        for (x in 1..5) {
            ch.send(x)
            delay(1000L)
        }
        ch.close()
    }

    for (i in ch) {
        println(i)
    }
}
