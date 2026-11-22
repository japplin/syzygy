package syzygy.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/** Connects the rendering's [BackHost] to the Android system Back dispatcher. */
@Composable
fun AndroidBackHost(
    host: BackHost = remember { BackHost() },
    content: @Composable () -> Unit,
) {
    BackHandler(enabled = host.canHandleBack) { host.dispatchBack() }
    host.Content(content)
}
