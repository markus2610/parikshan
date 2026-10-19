package sample.app

import androidx.compose.runtime.Composable

/** Opens the platform's native file picker and shows how many files came back; empty where unsupported. */
@Composable
expect fun FilePickerDemo()
