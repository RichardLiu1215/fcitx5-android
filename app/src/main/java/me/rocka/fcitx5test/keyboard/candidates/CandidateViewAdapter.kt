package me.rocka.fcitx5test.keyboard.candidates

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.collection.lruCache
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import me.rocka.fcitx5test.R
import splitties.dimensions.dp
import splitties.resources.dimen
import splitties.resources.dimenPxSize
import splitties.views.dsl.core.matchParent
import splitties.views.dsl.core.textView
import splitties.views.gravityCenter
import kotlin.properties.Delegates

abstract class CandidateViewAdapter :
    RecyclerView.Adapter<CandidateViewAdapter.ViewHolder>() {
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val textView = itemView as TextView
        var idx = -1
    }

    var offset by Delegates.observable(0) { _, old, new ->
        if (old != new)
            notifyItemRangeChanged(0, new)
    }

    private var candidates: Array<String> = arrayOf()

    fun getCandidateAt(position: Int) = candidates[position + offset]

    @SuppressLint("NotifyDataSetChanged")
    fun updateCandidates(data: Array<String>) {
        candidates = data
        notifyDataSetChanged()
    }

    // cache measureWidth
    private val measuredWidths = lruCache<String, Float>(200)

    fun measureWidth(position: Int): Float {
        val candidate = getCandidateAt(position)
        return measuredWidths[candidate] ?: run {
            val paint = Paint()
            val bounds = Rect()
            // 20f here is chosen randomly, since we only care about the ratio
            paint.textSize = 20f
            paint.getTextBounds(candidate, 0, candidate.length, bounds)
            (bounds.width() / 20f).also { measuredWidths.put(candidate, it) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = parent.context.textView {
            layoutParams = GridLayoutManager.LayoutParams(matchParent, dp(40))
            gravity = gravityCenter
            setTextSize(TypedValue.COMPLEX_UNIT_PX, dimen(R.dimen.candidate_font_size))
            minWidth = dimenPxSize(R.dimen.candidate_min_width)
            isSingleLine = true
        }
        return ViewHolder(view).apply {
            itemView.setOnClickListener { onSelect(this.idx) }
            itemView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> onTouchDown()
                    MotionEvent.ACTION_BUTTON_PRESS -> v.performClick()
                }
                false
            }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = getCandidateAt(position)
        holder.idx = position + offset
    }

    override fun getItemCount() = candidates.size - offset

    abstract fun onTouchDown()

    abstract fun onSelect(idx: Int)
}