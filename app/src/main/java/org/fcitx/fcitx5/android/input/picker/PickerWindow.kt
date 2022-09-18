package org.fcitx.fcitx5.android.input.picker

import android.view.Gravity
import androidx.core.content.ContextCompat
import androidx.transition.Slide
import androidx.viewpager2.widget.ViewPager2
import kotlinx.coroutines.runBlocking
import org.fcitx.fcitx5.android.core.Action
import org.fcitx.fcitx5.android.data.punctuation.PunctuationManager
import org.fcitx.fcitx5.android.input.broadcast.InputBroadcastReceiver
import org.fcitx.fcitx5.android.input.dependency.fcitx
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.keyboard.*
import org.fcitx.fcitx5.android.input.wm.EssentialWindow
import org.fcitx.fcitx5.android.input.wm.InputWindow
import org.fcitx.fcitx5.android.input.wm.InputWindowManager
import org.mechdancer.dependency.manager.must

class PickerWindow(val data: List<Pair<String, Array<String>>>) :
    InputWindow.ExtendedInputWindow<PickerWindow>(), EssentialWindow, InputBroadcastReceiver {

    private val theme by manager.theme()
    private val windowManager: InputWindowManager by manager.must()
    private val commonKeyActionListener: CommonKeyActionListener by manager.must()
    private val fcitx by manager.fcitx()

    companion object : EssentialWindow.Key

    override val key: EssentialWindow.Key
        get() = PickerWindow

    private lateinit var pickerLayout: PickerLayout
    private lateinit var pickerPagesAdapter: PickerPagesAdapter

    private var punctuationEnabled = false

    override fun enterAnimation(lastWindow: InputWindow) = Slide().apply {
        slideEdge = Gravity.BOTTOM
    }.takeIf {
        // disable animation switching between keyboard
        lastWindow !is KeyboardWindow
    }

    override fun exitAnimation(nextWindow: InputWindow) = super.exitAnimation(nextWindow).takeIf {
        // disable animation switching between keyboard
        nextWindow !is KeyboardWindow
    }

    private val keyActionListener = KeyActionListener { it, source ->
        when (it) {
            is KeyAction.LayoutSwitchAction -> when (it.act) {
                NumberKeyboard.Name -> {
                    // Switch to NumberKeyboard before attaching KeyboardWindow
                    (windowManager.getEssentialWindow(KeyboardWindow) as KeyboardWindow)
                        .switchLayout(NumberKeyboard.Name)
                    // The real switchLayout (detachCurrentLayout and attachLayout) in KeyboardWindow is postponed,
                    // so we have to postpone attachWindow as well
                    ContextCompat.getMainExecutor(context).execute {
                        windowManager.attachWindow(KeyboardWindow)
                    }
                }
                else -> {
                    windowManager.attachWindow(KeyboardWindow)
                }
            }
            else -> {
                commonKeyActionListener.listener.onKeyAction(it, source)
            }
        }
    }

    override fun onCreateView() = PickerLayout(context, theme).apply {
        pickerLayout = this
        pickerPagesAdapter = PickerPagesAdapter(theme, keyActionListener, data)
        tabsUi.apply {
            setTabs(pickerPagesAdapter.categories)
            setOnTabClickListener { i ->
                pager.setCurrentItem(pickerPagesAdapter.getStartPageOfCategory(i), false)
            }
        }
        pager.apply {
            adapter = pickerPagesAdapter
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    tabsUi.activateTab(pickerPagesAdapter.getCategoryOfPage(position))
                }
            })
        }
    }

    override fun onCreateBarExtension() = pickerLayout.tabsUi.root

    override fun beforeAttached() {
        // Block UI for transformation
        runBlocking {
            transformPunctuation()
        }
    }

    override fun onAttached() {
        pickerLayout.embeddedKeyboard.keyActionListener = keyActionListener
    }

    override fun onDetached() {
        pickerLayout.embeddedKeyboard.keyActionListener = null
    }

    private suspend fun transformPunctuation() {
        // TODO Cache this value
        val mapping = PunctuationManager.load(fcitx).associate { it.key to it.mapping }
        val f = if (punctuationEnabled) { s: String -> mapping[s] } else { _ -> null }
        pickerPagesAdapter.applyTransformation(f)
    }

    override fun onStatusAreaUpdate(actions: Array<Action>) {
        punctuationEnabled = actions.any {
            // TODO A better way to check if punctuation mapping is enabled
            it.name == "punctuation" && it.icon == "fcitx-punc-active"
        }
    }

    override val showTitle = false
}