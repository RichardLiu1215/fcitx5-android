/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2023 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.keyboard

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import androidx.annotation.CallSuper
import androidx.annotation.DrawableRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.updateLayoutParams
import org.fcitx.fcitx5.android.core.InputMethodEntry
import org.fcitx.fcitx5.android.data.InputFeedbacks
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.data.prefs.ManagedPreference
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.GestureType
import org.fcitx.fcitx5.android.input.keyboard.CustomGestureView.OnGestureListener
import org.fcitx.fcitx5.android.input.popup.PopupAction
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.input.popup.PopupComponent
import org.fcitx.fcitx5.android.utils.MeasureCache
import splitties.dimensions.dp
import splitties.views.dsl.constraintlayout.bottomOfParent
import splitties.views.dsl.constraintlayout.bottomToBottomOf
import splitties.views.dsl.constraintlayout.horizontalGuideline
import splitties.views.dsl.constraintlayout.lParams
import splitties.views.dsl.constraintlayout.leftOfParent
import splitties.views.dsl.constraintlayout.leftToRightOf
import splitties.views.dsl.constraintlayout.rightOfParent
import splitties.views.dsl.constraintlayout.rightToLeftOf
import splitties.views.dsl.constraintlayout.topOfParent
import splitties.views.dsl.constraintlayout.topToTopOf
import splitties.views.dsl.core.add

abstract class BaseKeyboard(
    context: Context,
    protected val theme: Theme,
    private val keyLayout: List<List<KeyDef>>
) : ConstraintLayout(context) {

    var keyActionListener: KeyActionListener? = null

    private val prefs = AppPrefs.getInstance()

    private val popupOnKeyPress by prefs.keyboard.popupOnKeyPress
    private val expandKeypressArea by prefs.keyboard.expandKeypressArea
    private val swipeSymbolDirection by prefs.keyboard.swipeSymbolDirection

    private val spaceSwipeMoveCursor = prefs.keyboard.spaceSwipeMoveCursor
    private val spaceKeys = mutableListOf<KeyView>()
    private val spaceSwipeChangeListener = ManagedPreference.OnChangeListener<Boolean> { _, v ->
        spaceKeys.forEach {
            it.swipeEnabled = v
        }
    }

    private val vivoKeypressWorkaround by prefs.advanced.vivoKeypressWorkaround

    var popup: PopupComponent? = null
    var popupActionListener: PopupActionListener? = null

    private val selectionSwipeThreshold = dp(10f)
    private val inputSwipeThreshold = dp(36f)

    // a rather large threshold effectively disables swipe of the direction
    private val disabledSwipeThreshold = dp(800f)

    private val bounds = Rect()
    private val keyRows: List<List<KeyView>>


    private var touchKey: KeyView? = null

    private val measureCache = MeasureCache()

    init {
        isMotionEventSplittingEnabled = true

        val guideLines = Array(keyLayout.size - 1) { index ->
            horizontalGuideline(heightRatio = (index + 1) / keyLayout.size.toFloat())
        }

        keyRows = keyLayout.map { it.map(::createKeyView) }
        keyRows.forEachIndexed { rowIndex, keys ->
            val row = keyLayout[rowIndex]
            var totalWidth = 0f
            keys.forEachIndexed { keyIndex, key ->
                add(key, lParams {
                    if (keyIndex == 0) {
                        leftOfParent()
                        horizontalChainStyle = LayoutParams.CHAIN_PACKED
                    } else {
                        leftToRightOf(keys[keyIndex - 1])
                    }
                    if (keyIndex == keys.size - 1) {
                        rightOfParent()
                        // for RTL
                        horizontalChainStyle = LayoutParams.CHAIN_PACKED
                    } else {
                        rightToLeftOf(keys[keyIndex + 1])
                    }

                    val def = row[keyIndex]
                    matchConstraintPercentWidth = def.appearance.percentWidth

                    if (rowIndex == 0) {
                        topOfParent()
                    } else {
                        topToTopOf(guideLines[rowIndex - 1])
                    }
                    if (rowIndex == keyRows.lastIndex) {
                        bottomOfParent()
                    } else {
                        bottomToBottomOf(guideLines[rowIndex])
                    }
                })

                row[keyIndex].appearance.percentWidth.let {
                    // 0f means fill remaining space, thus does not need expanding
                    totalWidth += if (it != 0f) it else 1f
                }
            }
            if (expandKeypressArea && totalWidth < 1f) {
                val free = (1f - totalWidth) / 2f
                keys.first().apply {
                    updateLayoutParams<LayoutParams> {
                        matchConstraintPercentWidth += free
                    }
                    layoutMarginLeft = free / (row.first().appearance.percentWidth + free)
                }
                keys.last().apply {
                    updateLayoutParams<LayoutParams> {
                        matchConstraintPercentWidth += free
                    }
                    layoutMarginRight = free / (row.last().appearance.percentWidth + free)
                }
            }
        }
        spaceSwipeMoveCursor.registerOnChangeListener(spaceSwipeChangeListener)
    }

    private fun createKeyView(def: KeyDef): KeyView {
        return when (def.appearance) {
            is KeyDef.Appearance.AltText -> AltTextKeyView(context, theme, def.appearance)
            is KeyDef.Appearance.ImageText -> ImageTextKeyView(context, theme, def.appearance)
            is KeyDef.Appearance.Text -> TextKeyView(context, theme, def.appearance)
            is KeyDef.Appearance.Image -> ImageKeyView(context, theme, def.appearance)
        }.apply {
            soundEffect = when (def) {
                is SpaceKey -> InputFeedbacks.SoundEffect.SpaceBar
                is MiniSpaceKey -> InputFeedbacks.SoundEffect.SpaceBar
                is BackspaceKey -> InputFeedbacks.SoundEffect.Delete
                is ReturnKey -> InputFeedbacks.SoundEffect.Return
                else -> InputFeedbacks.SoundEffect.Standard
            }
            if (def is SpaceKey) {
                spaceKeys.add(this)
                swipeEnabled = spaceSwipeMoveCursor.getValue()
                swipeRepeatEnabled = true
                swipeThresholdX = selectionSwipeThreshold
                swipeThresholdY = disabledSwipeThreshold
//                onGestureListener = OnGestureListener { _, event ->
//                    when (event.type) {
//                        GestureType.Move -> when (val count = event.countX) {
//                            0 -> false
//                            else -> {
//                                val sym =
//                                    if (count > 0) FcitxKeyMapping.FcitxKey_Right else FcitxKeyMapping.FcitxKey_Left
//                                val action = KeyAction.SymAction(KeySym(sym), KeyStates.Empty)
//                                repeat(count.absoluteValue) {
//                                    onAction(action)
//                                }
//                                true
//                            }
//                        }
//                        else -> false
//                    }
//                }
            } else if (def is BackspaceKey) {
                swipeEnabled = true
                swipeRepeatEnabled = true
                swipeThresholdX = selectionSwipeThreshold
                swipeThresholdY = disabledSwipeThreshold
//                onGestureListener = OnGestureListener { _, event ->
//                    when (event.type) {
//                        GestureType.Move -> {
//                            val count = event.countX
//                            if (count != 0) {
//                                onAction(KeyAction.MoveSelectionAction(count))
//                                true
//                            } else false
//                        }
//                        GestureType.Up -> {
//                            onAction(KeyAction.DeleteSelectionAction(event.totalX))
//                            false
//                        }
//                        else -> false
//                    }
//                }
            }
            def.behaviors.forEach {
                when (it) {
                    is KeyDef.Behavior.Press -> {
                        setOnClickListener { _ ->
                            onAction(it.action)
                        }
                    }
                    is KeyDef.Behavior.LongPress -> {
                        setOnLongClickListener { _ ->
                            onAction(it.action)
                            true
                        }
                    }
                    is KeyDef.Behavior.Repeat -> {
                        repeatEnabled = true
                        onRepeatListener = { _ ->
                            onAction(it.action)
                        }
                    }
                    is KeyDef.Behavior.Swipe -> {
                        swipeEnabled = true
                        swipeThresholdX = disabledSwipeThreshold
                        swipeThresholdY = inputSwipeThreshold
//                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
//                        onGestureListener = OnGestureListener { view, event ->
//                            when (event.type) {
//                                GestureType.Up -> {
//                                    if (!event.consumed && swipeSymbolDirection.checkY(event.totalY)) {
//                                        onAction(it.action)
//                                        true
//                                    } else {
//                                        false
//                                    }
//                                }
//                                else -> false
//                            } || oldOnGestureListener.onGesture(view, event)
//                        }
                    }
                    is KeyDef.Behavior.DoubleTap -> {
                        doubleTapEnabled = true
                        onDoubleTapListener = { _ ->
                            onAction(it.action)
                        }
                    }
                }
            }
            def.popup?.forEach {
                when (it) {
                    // TODO: gesture processing middleware
                    is KeyDef.Popup.Menu -> {
                        setOnLongClickListener { view ->
                            view as KeyView
                            onPopupAction(PopupAction.ShowMenuAction(view.id, it, view.bounds))
                            // do not consume this LongClick gesture
                            false
                        }
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        swipeEnabled = true
                        onGestureListener = OnGestureListener { view, event ->
                            view as KeyView
                            when (event.type) {
                                GestureType.Move -> {
                                    onPopupChangeFocus(view.id, event.x, event.y)
                                }
                                GestureType.Up -> {
                                    onPopupTrigger(view.id)
                                }
                                else -> false
                            } || oldOnGestureListener.onGesture(view, event)
                        }
                    }
                    is KeyDef.Popup.Keyboard -> {
                        setOnLongClickListener { view ->
                            view as KeyView
                            onPopupAction(PopupAction.ShowKeyboardAction(view.id, it, view.bounds))
                            // do not consume this LongClick gesture
                            false
                        }
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        swipeEnabled = true
                        onGestureListener = OnGestureListener { view, event ->
                            view as KeyView
                            when (event.type) {
                                GestureType.Move -> {
                                    onPopupChangeFocus(view.id, event.x, event.y)
                                }
                                GestureType.Up -> {
                                    onPopupTrigger(view.id)
                                }
                                else -> false
                            } || oldOnGestureListener.onGesture(view, event)
                        }
                    }
                    is KeyDef.Popup.AltPreview -> {
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        onGestureListener = OnGestureListener { view, event ->
                            view as KeyView
                            if (popupOnKeyPress) {
                                when (event.type) {
                                    GestureType.Down -> onPopupAction(
                                        PopupAction.PreviewAction(view.id, it.content, view.bounds)
                                    )
                                    GestureType.Move -> {
                                        // val triggered = swipeSymbolDirection.checkY(event.totalY)
                                        // val text = if (triggered) it.alternative else it.content
                                        // onPopupAction(
                                        //    PopupAction.PreviewUpdateAction(view.id, text)
                                        // )
                                    }
                                    GestureType.Up -> {
                                        onPopupAction(PopupAction.DismissAction(view.id))
                                    }
                                }
                            }
                            // never consume gesture in preview popup
                            oldOnGestureListener.onGesture(view, event)
                        }
                    }
                    is KeyDef.Popup.Preview -> {
                        val oldOnGestureListener = onGestureListener ?: OnGestureListener.Empty
                        onGestureListener = OnGestureListener { view, event ->
                            view as KeyView
                            if (popupOnKeyPress) {
                                when (event.type) {
                                    GestureType.Down -> onPopupAction(
                                        PopupAction.PreviewAction(view.id, it.content, view.bounds)
                                    )
                                    GestureType.Up -> {
                                        onPopupAction(PopupAction.DismissAction(view.id))
                                    }
                                    else -> {}
                                }
                            }
                            // never consume gesture in preview popup
                            oldOnGestureListener.onGesture(view, event)
                        }
                    }
                }
            }
        }
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val (x, y) = intArrayOf(0, 0).also { getLocationInWindow(it) }
        bounds.set(x, y, x + width, y + height)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // intercept ACTION_DOWN and all following events will go to parent's onTouchEvent
        // return if (vivoKeypressWorkaround && ev.actionMasked == MotionEvent.ACTION_DOWN) true
        // else super.onInterceptTouchEvent(ev)
        return true
    }

    private fun dispatchKeyTouchEvent(keyView: KeyView, event: MotionEvent, action: Int? = null): Boolean {
        val transformedEvent = MotionEvent.obtain(event)
        if (action != null) {
            event.action = action
        }
        event.offsetLocation(-(keyView.left).toFloat(), -(keyView.top).toFloat())
        val result = keyView.dispatchTouchEvent(event)
        transformedEvent.recycle()
        return result
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val x = event.x
        val y = event.y
        val currentKey = touchKey

        if (action == MotionEvent.ACTION_CANCEL) {
            touchKey = null
            if (currentKey != null) dispatchKeyTouchEvent(currentKey, event)
            super.onTouchEvent(event)
            return true
        }

        if (action == MotionEvent.ACTION_UP) {
            touchKey = null
        }

        if (currentKey != null) {
            if (popup?.isPopupKeyboardUiShown(currentKey.id) == true
                || (x >= currentKey.left
                        && x < currentKey.right
                        && y >= currentKey.top
                        && y < currentKey.bottom)) {
                dispatchKeyTouchEvent(currentKey, event)
                super.onTouchEvent(event)
                return true
            } else {
                touchKey = null
                dispatchKeyTouchEvent(currentKey, event, MotionEvent.ACTION_CANCEL)
            }
        }

        val newKey = keyRows.find {
            it[0].top <= y && it[0].bottom > y
        }?.find {
            it.left <= x && it.right > x && it.top <= y && it.bottom > y
        }

        if (newKey != null) {
            touchKey = newKey
            dispatchKeyTouchEvent(newKey, event, MotionEvent.ACTION_DOWN)
        }

        super.onTouchEvent(event)
        return true
    }

    @CallSuper
    protected open fun onAction(
        action: KeyAction,
        source: KeyActionListener.Source = KeyActionListener.Source.Keyboard
    ) {
        keyActionListener?.onKeyAction(action, source)
    }

    @CallSuper
    protected open fun onPopupAction(action: PopupAction) {
        popupActionListener?.onPopupAction(action)
    }

    private fun onPopupChangeFocus(viewId: Int, x: Float, y: Float): Boolean {
        val changeFocusAction = PopupAction.ChangeFocusAction(viewId, x, y)
        popupActionListener?.onPopupAction(changeFocusAction)
        return changeFocusAction.outResult
    }

    private fun onPopupTrigger(viewId: Int): Boolean {
        val triggerAction = PopupAction.TriggerAction(viewId)
        // ask popup keyboard whether there's a pending KeyAction
        onPopupAction(triggerAction)
        val action = triggerAction.outAction ?: return false
        onAction(action, KeyActionListener.Source.Popup)
        onPopupAction(PopupAction.DismissAction(viewId))
        return true
    }

    open fun onAttach() {
        // do nothing by default
    }

    open fun onReturnDrawableUpdate(@DrawableRes returnDrawable: Int) {
        // do nothing by default
    }

    open fun onPunctuationUpdate(mapping: Map<String, String>) {
        // do nothing by default
    }

    open fun onInputMethodUpdate(ime: InputMethodEntry) {
        // do nothing by default
    }

    open fun onDetach() {
        // do nothing by default
    }

}