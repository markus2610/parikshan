/**
 * Answers `java.awt.FileDialog` opens from test-queued results so the native picker never shows.
 *
 * The app under test opens a FileDialog directly, so there is no subclass or peer seam to replace.
 * On the first queued result this installs ByteBuddy advice on `java.awt.Dialog.show()` through a
 * self-attached agent: for a FileDialog the advice skips the original body (no native peer, no modal
 * loop) after writing the queued selection into the dialog's file, directory and files fields.
 * The interceptor lives only in the test-mode app process started by Parikshan.
 */
package io.github.aryapreetam.parikshan.server

import io.github.aryapreetam.parikshan.protocol.FilePickerMode
import net.bytebuddy.agent.ByteBuddyAgent
import net.bytebuddy.agent.builder.AgentBuilder
import net.bytebuddy.asm.Advice
import net.bytebuddy.description.method.MethodDescription
import net.bytebuddy.utility.JavaModule
import net.bytebuddy.matcher.ElementMatchers.named
import net.bytebuddy.matcher.ElementMatchers.none
import net.bytebuddy.matcher.ElementMatchers.takesArguments
import java.awt.Dialog
import java.awt.FileDialog
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.function.Predicate

internal object DesktopFileDialogInterceptor {
  /** System property holding the hook; the advice runs inside `java.awt.Dialog` and sees only `java.*` types. */
  internal const val HOOK_PROPERTY = "io.github.aryapreetam.parikshan.fileDialogHook"

  internal data class QueuedResult(val mode: FilePickerMode, val paths: List<File>) {
    fun describe(): String = "${mode.name.lowercase()} ${paths.map { it.path }}"
  }

  private val queue = ConcurrentLinkedQueue<QueuedResult>()
  private val unexpectedOpens = ConcurrentLinkedQueue<String>()

  @Volatile
  private var installed = false

  /** Queues the result for the next FileDialog open. Returns an error message, or null when queued. */
  fun enqueue(mode: FilePickerMode, paths: List<String>): String? {
    val files = paths.map { File(it).absoluteFile }
    val invalid = validate(mode, files)
    if (invalid != null) return invalid
    val installError = runCatching { ensureInstalled() }.exceptionOrNull()
    if (installError != null) {
      return "file picker interception unavailable: $installError (start the app JVM with -Djdk.attach.allowAttachSelf=true)"
    }
    queue.add(QueuedResult(mode, files))
    return null
  }

  /** Returns an error naming every result never consumed and every open nothing was queued for, or null. */
  fun verify(): String? {
    val problems = queue.map { "queued file picker result never consumed: ${it.describe()}" } +
      unexpectedOpens.map { "FileDialog \"$it\" opened with no queued result (answered as cancel)" }
    return problems.takeIf { it.isNotEmpty() }?.joinToString("; ")
  }

  /** Drops queued results and recorded opens; called on every test reset. */
  fun reset() {
    queue.clear()
    unexpectedOpens.clear()
  }

  /** Called by the advice for every `Dialog.show()`; true means the original show is skipped. */
  internal fun answer(dialog: Any?): Boolean {
    if (dialog !is FileDialog) return false
    val result = queue.poll()
    if (result == null) {
      unexpectedOpens.add(dialog.title.orEmpty())
      apply(dialog, QueuedResult(FilePickerMode.Cancel, emptyList()))
    } else {
      apply(dialog, result)
    }
    return true
  }

  private fun validate(mode: FilePickerMode, files: List<File>): String? = when (mode) {
    FilePickerMode.Files -> files.firstOrNull { !it.isFile }?.let { "no such file: ${it.path}" }
      ?: "selectFiles needs at least one path".takeIf { files.isEmpty() }
    FilePickerMode.Directory -> if (files.size != 1) "selectDirectory needs exactly one path"
      else "no such directory: ${files[0].path}".takeUnless { files[0].isDirectory }
    FilePickerMode.Cancel -> "cancelPicker takes no paths".takeIf { files.isNotEmpty() }
  }

  private fun apply(dialog: FileDialog, result: QueuedResult) {
    val first = result.paths.firstOrNull()
    dialog.directory = first?.parentFile?.path?.let { it + File.separator }
    dialog.file = first?.name
    filesField.set(dialog, result.paths.takeIf { result.mode == FilePickerMode.Files }?.toTypedArray())
  }

  private val filesField by lazy {
    FileDialog::class.java.getDeclaredField("files").apply { isAccessible = true }
  }

  @Synchronized
  private fun ensureInstalled() {
    if (installed) return
    val instrumentation = ByteBuddyAgent.install()
    val awt = Dialog::class.java.module
    instrumentation.redefineModule(
      awt, emptySet(), emptyMap(), mapOf("java.awt" to setOf(javaClass.module)), emptySet(), emptyMap()
    )
    System.getProperties()[HOOK_PROPERTY] = Predicate<Any?> { answer(it) }
    val failure = TransformFailure()
    AgentBuilder.Default()
      .disableClassFormatChanges()
      .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
      .with(failure)
      .ignore(none())
      .type(named("java.awt.Dialog"))
      .transform { builder, _, _, _, _ ->
        builder.visit(Advice.to(DialogShowAdvice::class.java).on(named<MethodDescription>("show").and(takesArguments(0))))
      }
      .installOn(instrumentation)
    failure.error?.let { throw IllegalStateException("could not instrument java.awt.Dialog", it) }
    installed = true
  }
}

/** Captures a failed retransformation so a queued result never falls through to the native picker. */
private class TransformFailure : AgentBuilder.Listener.Adapter() {
  @Volatile
  var error: Throwable? = null

  override fun onError(typeName: String, classLoader: ClassLoader?, module: JavaModule?, loaded: Boolean, throwable: Throwable) {
    error = throwable
  }
}

/**
 * Advice inlined into `java.awt.Dialog.show()`. It may reference only `java.*` types, so it reaches the
 * interceptor through a [Predicate] stored in the system properties. A true return skips the original body.
 */
internal object DialogShowAdvice {
  @JvmStatic
  @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue::class)
  fun enter(@Advice.This dialog: Any?): Boolean {
    val hook = System.getProperties()[DesktopFileDialogInterceptor.HOOK_PROPERTY]
    @Suppress("UNCHECKED_CAST")
    return hook is Predicate<*> && (hook as Predicate<Any?>).test(dialog)
  }
}
