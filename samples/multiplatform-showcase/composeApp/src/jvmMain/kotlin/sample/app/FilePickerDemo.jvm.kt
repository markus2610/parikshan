package sample.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.awt.FileDialog
import java.awt.Frame

/** Desktop: a multi-select java.awt.FileDialog, the picker Parikshan's selectFiles()/cancelPicker() answer. */
@Composable
actual fun FilePickerDemo() {
  var picked by remember { mutableStateOf<Int?>(null) }
  Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 12.dp)) {
    Button(onClick = { picked = pickFiles() }, modifier = Modifier.testTag("pick_button")) {
      Text("Pick files")
    }
    val label = when (picked) {
      null -> "picked_idle"
      0 -> "picked_none"
      else -> "picked_count_$picked"
    }
    Text(text = label.substringAfter("picked_").replace('_', ' '), modifier = Modifier.testTag(label))
  }
}

private fun pickFiles(): Int {
  val dialog = FileDialog(null as Frame?, "Select media", FileDialog.LOAD)
  dialog.isMultipleMode = true
  dialog.isVisible = true
  return dialog.files.size
}
