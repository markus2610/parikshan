package sample.app

import io.github.aryapreetam.parikshan.e2eTest
import io.github.aryapreetam.parikshan.isDesktop
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/** Desktop only: the app's native java.awt.FileDialog answered by selectFiles()/cancelPicker(), never shown. */
class FilePickerIntegrationTest {

  @Test
  fun testMultiSelectReturnsQueuedFiles() = e2eTest {
    if (!isDesktop()) return@e2eTest
    selectFiles("fixtures/a.png", "fixtures/b.png")
    click("pick_button")
    assertVisible("picked_count_2")
  }

  @Test
  fun testCancelReturnsNoSelection() = e2eTest {
    if (!isDesktop()) return@e2eTest
    selectFiles("fixtures/a.png", "fixtures/b.png")
    click("pick_button")
    assertVisible("picked_count_2")

    cancelPicker()
    click("pick_button")
    assertVisible("picked_none")
  }

  @Test
  fun testUnconsumedSelectionFailsTheTest() {
    var desktop = false
    val failure = runCatching {
      e2eTest {
        desktop = isDesktop()
        if (desktop) selectFiles("fixtures/b.png")
      }
    }.exceptionOrNull()

    if (!desktop) return
    assertTrue(failure is AssertionError, "expected an AssertionError, got $failure")
    assertContains(failure.message.orEmpty(), "never consumed")
    assertContains(failure.message.orEmpty(), "b.png")
  }
}
