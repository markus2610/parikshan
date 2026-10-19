/** Checks the file picker DSL: platform fail-fast and the end-of-test unconsumed-queue check. */
package io.github.aryapreetam.parikshan

import io.github.aryapreetam.parikshan.protocol.Command
import io.github.aryapreetam.parikshan.protocol.FilePickerMode
import io.github.aryapreetam.parikshan.protocol.Response
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@OptIn(InternalParikshanApi::class)
class FilePickerDslTest {
  @Test
  fun select_files_fails_fast_on_unsupported_platform() {
    val driver = RecordingDriver(platform = "android")
    val error = assertFailsWith<AssertionError> {
      runSuspend { e2eTest(driver, E2ETestConfig(captureScreenshotOnFailure = false)) { selectFiles("a.png") } }
    }
    assertContains(error.message.orEmpty(), "file picker control is not supported on android yet")
    assertTrue(driver.sent.none { it is Command.QueueFilePickerResult })
  }

  @Test
  fun queued_selection_is_sent_and_verified_at_test_end() {
    val driver = RecordingDriver(platform = "desktop")
    runSuspend { e2eTest(driver) { selectFiles("/x/a.png", "/x/b.png") } }

    val queued = driver.sent.filterIsInstance<Command.QueueFilePickerResult>().single()
    assertEquals(FilePickerMode.Files, queued.mode)
    assertEquals(listOf("/x/a.png", "/x/b.png"), queued.paths)
    assertEquals(1, driver.sent.count { it is Command.VerifyFilePickerQueue })
  }

  @Test
  fun unconsumed_queue_error_fails_the_test() {
    val driver = RecordingDriver(platform = "desktop", verifyError = "unconsumed picker result: files [/x/a.png]")
    val error = assertFailsWith<AssertionError> {
      runSuspend { e2eTest(driver, E2ETestConfig(captureScreenshotOnFailure = false)) { cancelPicker() } }
    }
    assertContains(error.message.orEmpty(), "/x/a.png")
  }

  @Test
  fun test_without_picker_use_sends_no_verify() {
    val driver = RecordingDriver(platform = "desktop")
    runSuspend { e2eTest(driver) { } }
    assertTrue(driver.sent.none { it is Command.VerifyFilePickerQueue })
  }

  private class RecordingDriver(
    private val platform: String,
    private val verifyError: String? = null
  ) : TestDriver {
    val sent = mutableListOf<Command>()
    override val targetPlatform: String get() = platform

    override suspend fun send(command: Command): Response {
      sent += command
      if (command is Command.VerifyFilePickerQueue && verifyError != null) {
        return Response.Error(command.id, verifyError)
      }
      return Response.Ok(command.id)
    }

    override suspend fun close() {}
  }

  /** Runs a block whose fake driver never suspends; avoids a coroutines-test dependency. */
  private fun runSuspend(block: suspend () -> Unit) {
    var outcome: Result<Unit>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { outcome = it })
    checkNotNull(outcome) { "block suspended" }.getOrThrow()
  }
}
