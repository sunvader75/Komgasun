package com.sunvader.komgasun.ui.reader.epub

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Recorre los ContextWrapper hasta encontrar Activity; devuelve null si no la hay. */
private tailrec fun Context.readerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.readerActivity()
    else -> null
}

/**
 * Oculta barras del sistema al leer y las muestra con los controles; reaplica el estado al reanudar.
 * Restaura visibilidad y comportamiento capturados al retirar el efecto. Sin ventana no hace nada.
 */
@Composable
internal fun EpubImmersiveMode(showControls: Boolean) {
    val activity = LocalContext.current.readerActivity()
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, view, lifecycle, showControls) {
        val window = activity?.window
        if (window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val insets = ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val previousBehavior = controller.systemBarsBehavior

        /** Permite barras transitorias con gesto y aplica la visibilidad solicitada a ambas barras. */
        fun updateBars(show: Boolean) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (show) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) updateBars(showControls)
        }
        lifecycle.addObserver(observer)
        updateBars(showControls)

        onDispose {
            lifecycle.removeObserver(observer)
            controller.systemBarsBehavior = previousBehavior
            if (statusVisible) controller.show(WindowInsetsCompat.Type.statusBars())
            else controller.hide(WindowInsetsCompat.Type.statusBars())
            if (navigationVisible) controller.show(WindowInsetsCompat.Type.navigationBars())
            else controller.hide(WindowInsetsCompat.Type.navigationBars())
        }
    }
}
