package com.twinfinder.app.ui.clean

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.IntentCompat
import coil.load
import com.google.android.material.button.MaterialButton
import com.twinfinder.app.R
import com.twinfinder.app.data.Media

/** Foto em tela cheia com zoom (pinça, arrastar, dois toques) ou vídeo com controles. */
class FullScreenActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri: Uri = requireNotNull(IntentCompat.getParcelableExtra(intent, EXTRA_URI, Uri::class.java))
        val root = FrameLayout(this).apply { setBackgroundColor(0xFF000000.toInt()); fitsSystemWindows = true }
        if (intent.getBooleanExtra(EXTRA_VIDEO, false)) {
            val video = VideoView(this)
            root.addView(video, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            video.setMediaController(MediaController(this).also { it.setAnchorView(video) })
            video.setVideoURI(uri)
            video.setOnPreparedListener { video.start() }
        } else {
            root.addView(ZoomImageView(this).apply { load(uri) }, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
            setIconResource(R.drawable.ic_close)
            iconTint = android.content.res.ColorStateList.valueOf(0xFFFFFFFF.toInt())
            contentDescription = getString(R.string.cancel)
            setOnClickListener { finish() }
        }, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END))
        setContentView(root)
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_VIDEO = "video"
        fun intent(context: Context, media: Media) = Intent(context, FullScreenActivity::class.java)
            .putExtra(EXTRA_URI, media.uri)
            .putExtra(EXTRA_VIDEO, media.isVideo)
    }
}

/** ImageView com zoom de 1x a 5x. Fica centralizada e não deixa a imagem "fugir" da tela. */
class ZoomImageView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : AppCompatImageView(context, attrs) {

    private val m = Matrix()
    private var scale = 1f

    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val target = (scale * d.scaleFactor).coerceIn(1f, 5f)
            val f = target / scale
            scale = target
            m.postScale(f, f, d.focusX, d.focusY)
            clamp()
            return true
        }
    })

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (scale <= 1f) return false
            m.postTranslate(-dx, -dy)
            clamp()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scale > 1f) reset() else {
                scale = 2.5f
                m.postScale(2.5f, 2.5f, e.x, e.y)
                clamp()
            }
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        reset()
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        reset()
    }

    /** Volta ao "caber na tela", centralizado. */
    private fun reset() {
        val d = drawable ?: return
        if (width == 0 || height == 0 || d.intrinsicWidth <= 0) return
        val fit = minOf(width.toFloat() / d.intrinsicWidth, height.toFloat() / d.intrinsicHeight)
        m.reset()
        m.postScale(fit, fit)
        m.postTranslate((width - d.intrinsicWidth * fit) / 2, (height - d.intrinsicHeight * fit) / 2)
        scale = 1f
        imageMatrix = m
    }

    private fun clamp() {
        val d = drawable ?: return
        val r = RectF(0f, 0f, d.intrinsicWidth.toFloat(), d.intrinsicHeight.toFloat())
        m.mapRect(r)
        val dx = when {
            r.width() <= width -> (width - r.width()) / 2 - r.left
            r.left > 0 -> -r.left
            r.right < width -> width - r.right
            else -> 0f
        }
        val dy = when {
            r.height() <= height -> (height - r.height()) / 2 - r.top
            r.top > 0 -> -r.top
            r.bottom < height -> height - r.bottom
            else -> 0f
        }
        m.postTranslate(dx, dy)
        imageMatrix = m
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaler.onTouchEvent(event)
        gestures.onTouchEvent(event)
        return true
    }
}
