package com.twinfinder.app.ui.common

import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.Scale
import com.twinfinder.app.R
import com.twinfinder.app.data.Media
import com.twinfinder.app.databinding.ItemThumbBinding

/** Miniatura quadrada: a altura acompanha a largura dada pela grade. */
class SquareFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) = super.onMeasure(widthMeasureSpec, widthMeasureSpec)
}

fun ImageView.loadThumb(media: Media, sizePx: Int = 300) = load(media.uri) {
    size(sizePx)
    scale(Scale.FILL)
}

/** Feedback tátil sutil para toques rápidos e transições de swipe. */
fun View.hapticTick() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    } else {
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

/** Feedback tátil de confirmação para ações importantes (decisões, botões de ação). */
fun View.hapticConfirm() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    } else {
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

/** Pequeno "pulo" elástico ao tocar num botão (ajuste "Animar os botões"). */
fun View.bounce(enabled: Boolean) {
    if (!enabled) return
    animate().scaleX(0.82f).scaleY(0.82f).setDuration(70)
        .setInterpolator(AccelerateInterpolator())
        .withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(130)
                .setInterpolator(OvershootInterpolator(2.2f)).start()
        }.start()
}

/** Como uma miniatura deve aparecer. */
enum class ThumbMark { NONE, KEEP, DELETE, MOVE, SELECTED, OFF }

/**
 * Adapter de miniaturas reaproveitado na faixa do topo, na revisão, nas duplicatas e na lixeira.
 * [fixedSizePx] fixa o tamanho (faixa horizontal); sem ele, a grade decide a largura.
 */
class ThumbAdapter(
    private val fixedSizePx: Int? = null,
    private val onLongClick: ((Int) -> Boolean)? = null,
    private val onClick: (Int) -> Unit,
) : RecyclerView.Adapter<ThumbAdapter.VH>() {

    constructor(fixedSizePx: Int? = null, onClick: (Int) -> Unit) : this(fixedSizePx, null, onClick)

    data class Item(val media: Media, val mark: ThumbMark = ThumbMark.NONE, val current: Boolean = false, val label: String? = null)

    var items: List<Item> = emptyList()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    class VH(val b: ItemThumbBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemThumbBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        fixedSizePx?.let { b.root.layoutParams = ViewGroup.LayoutParams(it, it) }
        return VH(b)
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = items[position]
        val b = holder.b
        val ctx = b.root.context
        b.image.loadThumb(item.media, fixedSizePx ?: 300)
        b.play.isVisible = item.media.isVideo
        b.frame.isVisible = item.current || item.mark == ThumbMark.SELECTED
        b.dim.isVisible = item.mark == ThumbMark.OFF
        val (icon, color) = when (item.mark) {
            ThumbMark.KEEP -> R.drawable.ic_check to R.color.keep
            ThumbMark.DELETE -> R.drawable.ic_delete to R.color.delete
            ThumbMark.MOVE -> R.drawable.ic_folder to R.color.move
            ThumbMark.SELECTED -> R.drawable.ic_check to R.color.brand
            else -> 0 to 0
        }
        b.badge.isVisible = icon != 0
        if (icon != 0) {
            b.badge.setImageResource(icon)
            b.badge.setColorFilter(ContextCompat.getColor(ctx, android.R.color.white))
            b.badge.setBackgroundColor(ContextCompat.getColor(ctx, color))
        }
        b.label.isVisible = item.label != null
        b.label.text = item.label
        b.root.setOnClickListener { holder.bindingAdapterPosition.takeIf { it >= 0 }?.let(onClick) }
        b.root.setOnLongClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos >= 0 && onLongClick != null) onLongClick.invoke(pos) else false
        }
    }
}
