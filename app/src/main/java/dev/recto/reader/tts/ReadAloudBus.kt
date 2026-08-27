package dev.recto.reader.tts

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** What the lock screen and notification buttons can ask for. */
enum class ReadAloudCommand { TOGGLE, NEXT, PREVIOUS, STOP }

/**
 * One-way channel from the notification to whoever is playing.
 *
 * A process-wide object rather than a bound service, because there is
 * exactly one reader open at a time and binding would mean lifecycle
 * plumbing for what is a handful of button presses.
 *
 * extraBufferCapacity keeps [emit] non-suspending: it is called from
 * Service.onStartCommand, which is not a coroutine, and a tryEmit that
 * silently dropped a Stop would leave the voice running with no way to
 * stop it.
 */
object ReadAloudBus {

    private val _commands = MutableSharedFlow<ReadAloudCommand>(
        replay = 0,
        extraBufferCapacity = 8
    )

    val commands: SharedFlow<ReadAloudCommand> = _commands.asSharedFlow()

    fun emit(command: ReadAloudCommand) {
        _commands.tryEmit(command)
    }
}
