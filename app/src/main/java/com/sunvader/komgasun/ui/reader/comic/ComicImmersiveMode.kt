package com.sunvader.komgasun.ui.reader.comic

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

/** Desenvuelve contextos hasta encontrar una Activity; devuelve null cuando no hay una accesible. */
private tailrec fun Context.readerActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.readerActivity()
    else -> null
}

/**
 * Oculta barras solo durante la composición del lector de cómics y al reanudarlo, permitiendo mostrarlas con un gesto.
 * Al salir restaura visibilidad y comportamiento previos y retira el observador; sin ventana no actúa.
 * No modifica la política global de barras ni la de los lectores PDF/EPUB.
 */
@Composable
internal fun ComicImmersiveMode() {
    val activity = LocalContext.current.readerActivity()
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, view, lifecycle) {
        val window = activity?.window
        if (window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, view)
        val insets = ViewCompat.getRootWindowInsets(view)
        val statusVisible = insets?.isVisible(WindowInsetsCompat.Type.statusBars()) ?: true
        val navigationVisible = insets?.isVisible(WindowInsetsCompat.Type.navigationBars()) ?: true
        val previousBehavior = controller.systemBarsBehavior
        /** Aplica barras transitorias por gesto y vuelve a ocultar ambas barras de sistema. */
        fun hideBars() {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hideBars()
        }
        lifecycle.addObserver(observer)
        hideBars()
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
