/**
 * Drives the FileDialog interceptor headless: a FileDialog allocated without its constructor (which throws
 * HeadlessException) is shown through the real `setVisible(true)` path. Without interception the real
 * `Dialog.show()` throws IllegalStateException for the uninitialized dialog, so no native panel can appear.
 */
package io.github.aryapreetam.parikshan.server

import io.github.aryapreetam.parikshan.protocol.FilePickerMode
import java.awt.Component
import java.awt.FileDialog
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopFileDialogInterceptorTest {
  private val dir: File = Files.createTempDirectory("parikshan-picker").toFile()
  private val a = File(dir, "a.png").apply { writeText("a") }
  private val b = File(dir, "b.png").apply { writeText("b") }

  @BeforeTest
  fun clearQueue() = DesktopFileDialogInterceptor.reset()

  @AfterTest
  fun cleanUp() {
    DesktopFileDialogInterceptor.reset()
    dir.deleteRecursively()
  }

  @Test
  fun multi_select_returns_queued_files_without_showing() {
    assertNull(DesktopFileDialogInterceptor.enqueue(FilePickerMode.Files, listOf(a.path, b.path)))
    val dialog = headlessFileDialog()

    dialog.isVisible = true

    assertEquals(listOf(a, b), dialog.files.toList())
    assertEquals("a.png", dialog.file)
    assertEquals(dir.path + File.separator, dialog.directory)
    assertNull(DesktopFileDialogInterceptor.verify())
  }

  @Test
  fun cancel_returns_no_selection() {
    assertNull(DesktopFileDialogInterceptor.enqueue(FilePickerMode.Files, listOf(a.path)))
    assertNull(DesktopFileDialogInterceptor.enqueue(FilePickerMode.Cancel, emptyList()))
    val dialog = headlessFileDialog()

    dialog.isVisible = true
    assertEquals("a.png", dialog.file)
    dialog.isVisible = true

    assertNull(dialog.file)
    assertEquals(0, dialog.files.size)
    assertNull(DesktopFileDialogInterceptor.verify())
  }

  @Test
  fun directory_mode_returns_directory_as_file_and_parent() {
    assertNull(DesktopFileDialogInterceptor.enqueue(FilePickerMode.Directory, listOf(dir.path)))
    val dialog = headlessFileDialog()

    dialog.isVisible = true

    assertEquals(dir, File(dialog.directory, dialog.file))
    assertEquals(0, dialog.files.size)
  }

  @Test
  fun unconsumed_result_is_named_by_verify() {
    assertNull(DesktopFileDialogInterceptor.enqueue(FilePickerMode.Files, listOf(a.path, b.path)))

    val error = DesktopFileDialogInterceptor.verify().orEmpty()

    assertContains(error, "never consumed")
    assertContains(error, a.path)
    assertContains(error, b.path)
  }

  @Test
  fun open_with_nothing_queued_is_answered_as_cancel_and_reported() {
    assertNull(DesktopFileDialogInterceptor.enqueue(FilePickerMode.Cancel, emptyList()))
    val dialog = headlessFileDialog()
    dialog.isVisible = true

    dialog.isVisible = true

    assertNull(dialog.file)
    assertContains(DesktopFileDialogInterceptor.verify().orEmpty(), "opened with no queued result")
  }

  @Test
  fun missing_file_is_rejected_before_queueing() {
    val error = DesktopFileDialogInterceptor.enqueue(FilePickerMode.Files, listOf(File(dir, "nope.png").path))

    assertContains(error.orEmpty(), "no such file")
    assertNull(DesktopFileDialogInterceptor.verify())
  }

  @Test
  fun reset_drops_queued_results() {
    assertNull(DesktopFileDialogInterceptor.enqueue(FilePickerMode.Files, listOf(a.path)))
    DesktopFileDialogInterceptor.reset()
    assertTrue(DesktopFileDialogInterceptor.verify() == null)
  }

  /** Allocates a FileDialog without its constructor; needs java.awt opened by the interceptor install. */
  private fun headlessFileDialog(): FileDialog {
    val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply { isAccessible = true }
    val unsafe = unsafeField.get(null) as sun.misc.Unsafe
    val dialog = unsafe.allocateInstance(FileDialog::class.java) as FileDialog
    Component::class.java.getDeclaredField("objectLock").apply { isAccessible = true }.set(dialog, Any())
    return dialog
  }
}
