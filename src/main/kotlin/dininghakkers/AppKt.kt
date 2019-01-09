package dininghakkers

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*


class Hakker(val name: String)

sealed class ChopstickState {
    object Available : ChopstickState()
    class Taken(val hakker: Hakker) : ChopstickState()
}

sealed class ChopstickCommand {
    class Take(val hakker: Hakker, val replyTo: SendChannel<ChopstickAnswer>) : ChopstickCommand()
    class Put(val hakker: Hakker) : ChopstickCommand()
}

sealed class ChopstickAnswer {
    class Taken(val name: String, val chopstick: SendChannel<ChopstickCommand>) : ChopstickAnswer()
    class Busy(val name: String) : ChopstickAnswer()
}

fun CoroutineScope.chopstickActor(name: String) = actor<ChopstickCommand> {
    var state: ChopstickState = ChopstickState.Available
    for (msg in channel) {
        when (state) {
            // When a Chopstick is taken by a hakker
            // It will refuse to be taken by other hakkers
            // But the owning hakker can put it back
            is ChopstickState.Taken ->
                when (msg) {
                    is ChopstickCommand.Take -> msg.replyTo.send(ChopstickAnswer.Busy(name))
                    is ChopstickCommand.Put -> {
                        if (msg.hakker.name == state.hakker.name) {
                            state = ChopstickState.Available
                        } else {
                            TODO("Chopstick can't be put back by another hakker")
                        }
                    }
                }
            // When a Chopstick is available, it can be taken by a hakker
            is ChopstickState.Available ->
                when (msg) {
                    is ChopstickCommand.Take -> {
                        state = ChopstickState.Taken(msg.hakker)
                        msg.replyTo.send(ChopstickAnswer.Taken(name, channel))
                    }
                    else -> TODO("Chopstick isn't taken")
                }
        }
    }
}

sealed class HakkerCommand {
    object Eat : HakkerCommand()
    object Think : HakkerCommand()
    class ChopstickResponse(val resp: ChopstickAnswer) : HakkerCommand()
}

sealed class HakkerState {
    object Waiting : HakkerState()
    object Thinking : HakkerState()
    object Hungry : HakkerState()
    object Eating : HakkerState()
    object FirstChopstickDenied : HakkerState()
    class WaitingForOtherChopstick(val waitingOn: SendChannel<ChopstickCommand>, val taken: Pair<String, SendChannel<ChopstickCommand>>) : HakkerState()
}

fun CoroutineScope.hakkerActor(name: String, left: SendChannel<ChopstickCommand>, right: SendChannel<ChopstickCommand>) = actor<HakkerCommand> {

    var state: HakkerState = HakkerState.Waiting

    for (msg in channel) {

        when (state) {
            is HakkerState.Waiting ->
                when (msg) {
                    HakkerCommand.Think -> {
                        println("$name starts to think")
                        state = HakkerState.Thinking
                        launch {
                            delay(5000L)
                            channel.send(HakkerCommand.Eat)
                        }
                    }
                }
            // When a hakker is thinking it can become hungry
            // and try to pick up its chopsticks and eat
            is HakkerState.Thinking ->
                when (msg) {
                    HakkerCommand.Eat -> {
                        state = HakkerState.Hungry
                        val ch = Channel<ChopstickAnswer>()
                        launch {
                            repeat(2) {
                                channel.send(HakkerCommand.ChopstickResponse(ch.receive()))
                            }
                            ch.close()
                        }
                        left.send(ChopstickCommand.Take(Hakker(name), ch))
                        right.send(ChopstickCommand.Take(Hakker(name), ch))
                    }
                    else -> TODO("When thinking hakker can only start eating, not thinking!")
                }
            // When a hakker is eating, he can decide to start to think,
            // then he puts down his chopsticks and starts to think
            is HakkerState.Eating ->
                when (msg) {
                    is HakkerCommand.Think -> {
                        println("$name puts down his chopsticks and starts to think")
                        left.send(ChopstickCommand.Put(Hakker(name)))
                        right.send(ChopstickCommand.Put(Hakker(name)))
                        state = HakkerState.Thinking
                        launch {
                            delay(5000L)
                            channel.send(HakkerCommand.Eat)
                        }
                    }
                    else -> TODO("When eating hakker can only start thinking, not eating! Received $msg")
                }
            is HakkerState.Hungry ->
                when (msg) {
                    is HakkerCommand.ChopstickResponse -> {
                        when (msg.resp) {
                            is ChopstickAnswer.Taken -> {
                                val waitingOn = when (msg.resp.chopstick) {
                                    left -> right
                                    right -> left
                                    else -> TODO("Received unknown chopstick: ${msg.resp.name}")
                                }
                                state = HakkerState.WaitingForOtherChopstick(waitingOn, Pair(msg.resp.name, msg.resp.chopstick))

                            }
                            is ChopstickAnswer.Busy -> {
                                state = HakkerState.FirstChopstickDenied
                            }
                        }
                    }
                }
            // When a hakker is waiting for the last chopstick it can either obtain it
            // and start eating, or the other chopstick was busy, and the hakker goes
            // back to think about how he should obtain his chopsticks :-)
            is HakkerState.WaitingForOtherChopstick ->
                when (msg) {
                    is HakkerCommand.ChopstickResponse -> {
                        when (msg.resp) {
                            is ChopstickAnswer.Taken -> {
                                if (msg.resp.chopstick == state.waitingOn) {
                                    println("$name has picked up ${state.taken.first} and ${msg.resp.name} and starts to eat")
                                }
                                state = HakkerState.Eating
                                launch {
                                    delay(5000L)
                                    channel.send(HakkerCommand.Think)
                                }
                            }
                            is ChopstickAnswer.Busy -> {
                                state.taken.second.send(ChopstickCommand.Put(Hakker(name)))
                                println("2nd chopstick is taken $name")
                                state = HakkerState.Thinking
                                launch {
                                    delay(10000L)
                                    channel.send(HakkerCommand.Eat)
                                }
                            }
                        }
                    }
                    else -> TODO("Unexpected message in state WaitingForOtherChopstick")
                }
            // When the results of the other grab comes back,
            // he needs to put it back if he got the other one.
            // Then go back and think and try to grab the chopsticks again
            is HakkerState.FirstChopstickDenied ->
                when (msg) {
                    is HakkerCommand.ChopstickResponse -> {
                        when (msg.resp) {
                            is ChopstickAnswer.Busy -> {
                                state = HakkerState.Thinking
                                launch {
                                    delay(10000L)
                                    channel.send(HakkerCommand.Eat)
                                }
                            }
                            is ChopstickAnswer.Taken -> {
                                println("1st chopstick is taken $name")
                                msg.resp.chopstick.send(ChopstickCommand.Put(Hakker(name)))
                                state = HakkerState.Thinking;
                                launch {
                                    delay(10000L)
                                    channel.send(HakkerCommand.Eat)
                                }
                            }

                        }
                    }
                    else -> TODO("Unexpected message in state FirstChopstickDenied")
                }
        }
    }
}

fun main() = runBlocking {

    val numberOfChopsticks = 3

    val chopsticks = (1..numberOfChopsticks).map {
        chopstickActor("chopstick-$it")
    }.toTypedArray()

    val hakkers = (1..numberOfChopsticks).map {
        val hakker = hakkerActor("hakker-$it", chopsticks[it - 1], chopsticks[it % numberOfChopsticks])
        launch {
            hakker.send(HakkerCommand.Think)
        }
    }
}
