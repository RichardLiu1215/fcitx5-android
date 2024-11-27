/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2024 Fcitx5 for Android Contributors
 */

package org.fcitx.fcitx5.android.input

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fcitx.fcitx5.android.core.FcitxEvent
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.data.theme.ThemePrefs
import org.fcitx.fcitx5.android.utils.MeasureCache
import org.fcitx.fcitx5.android.utils.navbarFrameHeight
import kotlin.math.max
import kotlin.time.measureTime

abstract class BaseInputView(
    val service: FcitxInputMethodService,
    val fcitx: FcitxConnection,
    val theme: Theme
) : ConstraintLayout(service) {

    protected abstract fun handleFcitxEvent(it: FcitxEvent<*>)

    private val measureCache = MeasureCache()

    private var eventHandlerJob: Job? = null

    private fun setupFcitxEventHandler() {
        eventHandlerJob = service.lifecycleScope.launch {
            fcitx.runImmediately { eventFlow }.collect {
                handleFcitxEvent(it)
            }
        }
    }

    var handleEvents = false
        set(value) {
            field = value
            if (field) {
                if (eventHandlerJob == null) {
                    setupFcitxEventHandler()
                }
            } else {
                eventHandlerJob?.cancel()
                eventHandlerJob = null
            }
        }

    private val navbarBackground by ThemeManager.prefs.navbarBackground

    protected fun getNavBarBottomInset(insets: WindowInsetsCompat): Int {
        if (navbarBackground != ThemePrefs.NavbarBackground.Full) {
            return 0
        }
        // use navigation bar insets when available
        val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
        // in case navigation bar insets goes wrong (eg. on LineageOS 21+ with gesture navigation)
        // use mandatory system gesture insets
        val mandatory = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
        var insetsBottom = max(navBars.bottom, mandatory.bottom)
        if (insetsBottom <= 0) {
            // check system gesture insets and fallback to navigation_bar_frame_height just in case
            val gesturesBottom = insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom
            if (gesturesBottom > 0) {
                insetsBottom = max(gesturesBottom, context.navbarFrameHeight())
            }
        }
        return insetsBottom
    }

    abstract fun onApplyWindowInsets(insets: WindowInsetsCompat)

    override fun onDetachedFromWindow() {
        handleEvents = false
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (measureCache.setMeasureSpecs(widthMeasureSpec, heightMeasureSpec)) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            measureCache.width = measuredWidth
            measureCache.height = measuredHeight
        } else {
            setMeasuredDimension(measureCache.width, measureCache.height)
        }
    }

    override fun requestLayout() {
        measureCache?.clear()
        super.requestLayout()
    }
}
