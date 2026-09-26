package com.rudra.texthistory

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class TextHistoryKeyboardService : InputMethodService() {

    private var isShifted = false
    private var isSymbols = false
    private val textBuffer = StringBuilder()
    private var isPasswordField = false
    private var keyboardView: LinearLayout? = null
    private var enterKey: TextView? = null

    private val handler = Handler(Looper.getMainLooper())

    // ── Colours & dimensions ──────────────────────────────────────────
    companion object {
        private const val BG_COLOR = 0xFF1B1B1F.toInt()
        private const val KEY_COLOR = 0xFF3A3A3D.toInt()
        private const val SPECIAL_KEY_COLOR = 0xFF2A2A2D.toInt()
        private const val SHIFT_ACTIVE_COLOR = 0xFF5A5AFF.toInt()
        private const val KEY_TEXT_COLOR = 0xFFE8E8E8.toInt()
        private const val RIPPLE_COLOR = 0x33FFFFFF
        private const val KEY_HEIGHT_DP = 42
        private const val KEY_RADIUS_DP = 8
        private const val KEY_MARGIN_DP = 3
        private const val BACKSPACE_INITIAL_DELAY = 400L
        private const val BACKSPACE_REPEAT_DELAY = 50L
    }

    // ── Key definitions ───────────────────────────────────────────────
    private val qwertyRow0 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val qwertyRow1 = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val qwertyRow2 = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    private val qwertyRow3 = listOf("z", "x", "c", "v", "b", "n", "m")

    private val symbolRow0 = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
    private val symbolRow1 = listOf("@", "#", "\$", "_", "&", "-", "+", "(", ")", "/")
    private val symbolRow2 = listOf("=", "*", "\"", "'", ":", ";", "!", "?")
    private val symbolRow3 = listOf("~", "`", "^", "\\", "|", "{", "}", "[", "]")

    // ── IME lifecycle ─────────────────────────────────────────────────
    override fun onCreateInputView(): View {
        val kb = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG_COLOR)
            val h = dpToPx(6)
            val w = dpToPx(4)
            setPadding(w, h, w, h)
        }
        keyboardView = kb
        populateKeyboard()
        return kb
    }

    override fun onStartInput(info: EditorInfo, restarting: Boolean) {
        super.onStartInput(info, restarting)
        if (!restarting) {
            saveBuffer()
            textBuffer.clear()
        }
        isPasswordField = isPasswordOrIncognito(info)
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        updateEnterKeyLabel(info)
    }

    override fun onFinishInput() {
        saveBuffer()
        textBuffer.clear()
        super.onFinishInput()
    }

    override fun onDestroy() {
        saveBuffer()
        textBuffer.clear()
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    // ── Password / incognito detection ────────────────────────────────
    private fun isPasswordOrIncognito(info: EditorInfo): Boolean {
        val inputType = info.inputType
        val cls = inputType and InputType.TYPE_MASK_CLASS
        val variation = inputType and InputType.TYPE_MASK_VARIATION

        if (cls == InputType.TYPE_CLASS_TEXT) {
            when (variation) {
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> return true
            }
        }
        if (cls == InputType.TYPE_CLASS_NUMBER &&
            variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
        ) return true

        if (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING != 0) return true

        return false
    }

    // ── Buffer helpers ────────────────────────────────────────────────
    private fun saveBuffer() {
        if (!isPasswordField && textBuffer.isNotEmpty()) {
            HistoryManager(this).save(textBuffer.toString())
        }
    }

    private fun commitChar(ch: String) {
        currentInputConnection?.commitText(ch, 1)
        if (!isPasswordField) textBuffer.append(ch)
    }

    private fun handleBackspace() {
        currentInputConnection?.deleteSurroundingText(1, 0)
        if (textBuffer.isNotEmpty()) textBuffer.deleteCharAt(textBuffer.length - 1)
    }

    private fun handleEnter() {
        saveBuffer()
        textBuffer.clear()

        val ic = currentInputConnection ?: return
        val action = currentInputEditorInfo?.let {
            it.imeOptions and EditorInfo.IME_MASK_ACTION
        } ?: EditorInfo.IME_ACTION_UNSPECIFIED

        when (action) {
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_DONE -> ic.performEditorAction(action)
            else -> sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    // ── Enter key label ───────────────────────────────────────────────
    private fun updateEnterKeyLabel(info: EditorInfo?) {
        val label = when (info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)) {
            EditorInfo.IME_ACTION_SEARCH -> "\uD83D\uDD0D"
            EditorInfo.IME_ACTION_SEND   -> "➤"
            EditorInfo.IME_ACTION_GO     -> "→"
            EditorInfo.IME_ACTION_DONE   -> "✓"
            else -> "↵"
        }
        enterKey?.text = label
    }

    // ── Build keyboard ────────────────────────────────────────────────
    private fun populateKeyboard() {
        val kb = keyboardView ?: return
        kb.removeAllViews()
        enterKey = null

        if (isSymbols) {
            kb.addView(createCharRow(symbolRow0))
            kb.addView(createCharRow(symbolRow1))
            kb.addView(createCharRow(symbolRow2, centred = true))
            kb.addView(createSymbolRow3())
        } else {
            kb.addView(createCharRow(qwertyRow0))
            kb.addView(createCharRow(qwertyRow1))
            kb.addView(createCharRow(qwertyRow2, centred = true))
            kb.addView(createQwertyRow3())
        }
        kb.addView(createBottomRow())

        currentInputEditorInfo?.let { updateEnterKeyLabel(it) }
    }

    // ── Row builders ──────────────────────────────────────────────────
    private fun createCharRow(
        chars: List<String>,
        centred: Boolean = false
    ): LinearLayout {
        val sidePad = if (centred) dpToPx(16) else 0
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = wrapRow()
            setPadding(sidePad, 0, sidePad, 0)
            for (ch in chars) addView(createCharKey(ch, 1f))
        }
    }

    private fun createCharKey(char: String, weight: Float): View {
        val display = if (!isSymbols && isShifted) char.uppercase() else char
        return makeKey(display, weight, KEY_COLOR) {
            val out = if (!isSymbols && isShifted) char.uppercase() else char
            commitChar(out)
            if (isShifted && !isSymbols) {
                isShifted = false
                populateKeyboard()
            }
        }
    }

    private fun createQwertyRow3(): LinearLayout {
        val shiftColor = if (isShifted) SHIFT_ACTIVE_COLOR else SPECIAL_KEY_COLOR
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = wrapRow()
            addView(makeKey("⇧", 1.5f, shiftColor) {
                isShifted = !isShifted
                populateKeyboard()
            })
            for (ch in qwertyRow3) addView(createCharKey(ch, 1f))
            addView(createBackspaceKey(1.5f))
        }
    }

    private fun createSymbolRow3(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = wrapRow()
            for (sym in symbolRow3) addView(createCharKey(sym, 1f))
            addView(createBackspaceKey(1f))
        }
    }

    private fun createBottomRow(): LinearLayout {
        val toggleLabel = if (isSymbols) "ABC" else "?123"
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = wrapRow()

            addView(makeKey(toggleLabel, 1.5f, SPECIAL_KEY_COLOR, textSizeSp = 14f) {
                isSymbols = !isSymbols
                isShifted = false
                populateKeyboard()
            })
            addView(createCharKey(",", 1f))
            addView(makeKey("", 5f, KEY_COLOR) { commitChar(" ") })
            addView(createCharKey(".", 1f))

            val enter = makeKey("↵", 1.5f, SPECIAL_KEY_COLOR) { handleEnter() }
            enterKey = enter as? TextView
            addView(enter)
        }
    }

    // ── Backspace with auto-repeat ────────────────────────────────────
    @SuppressLint("ClickableViewAccessibility")
    private fun createBackspaceKey(weight: Float): View {
        val key = makeKey("⌫", weight, SPECIAL_KEY_COLOR, clickAction = null)
        var pressed = false
        val repeat = object : Runnable {
            override fun run() {
                if (pressed) { handleBackspace(); handler.postDelayed(this, BACKSPACE_REPEAT_DELAY) }
            }
        }
        key.setOnTouchListener { v, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    pressed = true; v.isPressed = true
                    handleBackspace()
                    handler.postDelayed(repeat, BACKSPACE_INITIAL_DELAY); true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pressed = false; v.isPressed = false
                    handler.removeCallbacks(repeat); true
                }
                else -> false
            }
        }
        return key
    }

    // ── Generic key factory ───────────────────────────────────────────
    private fun makeKey(
        label: String,
        weight: Float,
        bgColor: Int,
        textSizeSp: Float = 18f,
        clickAction: (() -> Unit)? = {}
    ): View {
        val m = dpToPx(KEY_MARGIN_DP)
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(KEY_TEXT_COLOR)
            textSize = textSizeSp
            typeface = Typeface.DEFAULT
            includeFontPadding = false
            background = rippleDrawable(bgColor)
            isClickable = true
            isFocusable = false
            layoutParams = LinearLayout.LayoutParams(0, dpToPx(KEY_HEIGHT_DP), weight).apply {
                setMargins(m, m, m, m)
            }
            if (clickAction != null) setOnClickListener { clickAction() }
        }
    }

    // ── Drawables ─────────────────────────────────────────────────────
    private fun rippleDrawable(bgColor: Int): RippleDrawable {
        val r = dpToPx(KEY_RADIUS_DP).toFloat()
        val shape = GradientDrawable().apply { setColor(bgColor); cornerRadius = r }
        val mask  = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = r }
        return RippleDrawable(ColorStateList.valueOf(RIPPLE_COLOR), shape, mask)
    }

    // ── Utilities ─────────────────────────────────────────────────────
    private fun wrapRow() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).toInt()
}
