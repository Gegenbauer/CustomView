package me.gegenbauer.customview.navigation

import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.animation.PathInterpolatorCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.gegenbauer.customview.navigationview.R
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "CarNavigationView"

class CarNavigationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs), ProgressController {

    override var progress: Float = 0f
        set(value) {
            if (field != value && isDoingIntermediateAnim.get().not()) {
                field = value
                onProgressChanged()
            }
        }

    var isDoingIntermediateAnim = AtomicBoolean(false)

    private val searchState by lazy { SearchState(this) }
    private val nearbyState by lazy { NearbyState(this) }
    private val atFrontState by lazy { AtFrontState(this) }

    private var state: NavigationState = searchState
        set(value) {
            if (field != value) {
                Log.d(TAG, "[changeState] from ${field.javaClass.simpleName} to ${value.javaClass.simpleName}")
                field.onExitState()
                field = value
                field.initParams()
                field.onEnterState()
            }
        }

    internal val ac = AnimConfiguration(context, attrs)

    internal val centerPoint = CenterPoint(context, ac)
    internal val frontSector = FrontSector(context, ac)
    internal val emptyProgressBar = EmptyProgressBar(context, ac)
    internal val progressBar = ProgressBar(context, ac)
    internal val carArrow = CarArrow(context, ac)
    internal val iconCar = IconCar(context, ac)
    internal val iconCarBackground = IconCarBackground(context, ac)
    internal val carWaterWave = CarWaterWave(context, ac, this)

    private val uiParts = listOf(
        emptyProgressBar,
        progressBar,
        frontSector,
        centerPoint,
        carArrow,
        carWaterWave,
        iconCarBackground,
        iconCar,
    )

    private val scope = MainScope()
    private var smoothlySetProgressJob: Job? = null
    private var targetProgress = 0f

    init {
        state.initParams()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        uiParts.forEach { it.draw(canvas) }
    }

    private fun onProgressChanged() {
        state.progress = progress
        if (progress == 1f || progress == 0f) {
            state = atFrontState
        } else {
            setNearbyState(false)
        }
    }

    fun setNearbyState(nearby: Boolean) {
        state = if (nearby) {
            nearbyState
        } else {
            if (progress == 1f) atFrontState else searchState
        }
    }

    fun smoothlySetProgress(progress: Float) {
        targetProgress = progress
        if (smoothlySetProgressJob?.isActive == true) {
            return
        }
        startSmoothlyUpdateProgressTask()
    }

    private fun startSmoothlyUpdateProgressTask() {
        val lastJob = smoothlySetProgressJob
        smoothlySetProgressJob = scope.launch {
            lastJob?.cancelAndJoin()
            while (isActive) {
                // update per 5ms, total duration is 200ms
                val delta = (targetProgress - progress) / 20
                if (progress == targetProgress) {
                    break
                }
                if (abs(delta) < 0.0001) {
                    progress = targetProgress
                    break
                }
                val newProgress = progress + delta
                progress = when {
                    (delta > 0 && newProgress >= targetProgress) || (delta < 0 && newProgress <= targetProgress) -> {
                        targetProgress
                    }

                    newProgress <= 0f -> {
                        0f
                    }

                    newProgress >= 1f -> {
                        1f
                    }

                    else -> {
                        newProgress
                    }
                }
                delay(5)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = width + ac.carIconSize
        setMeasuredDimension(width, height)
        ac.viewSize.set(width, height)
        uiParts.forEach { it.onDimensChanged() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopAllAnimWhenInvisible()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == VISIBLE) resumeAllAnimWhenVisible()
        else stopAllAnimWhenInvisible()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resumeAllAnimWhenVisible()
    }

    private fun stopAllAnimWhenInvisible() {
        state.onExitState()
    }

    private fun resumeAllAnimWhenVisible() {
        state.onEnterState()
    }
}

class AnimConfiguration(context: Context, attrs: AttributeSet? = null) {
    val carIconSize: Int
    val carIconBgRadius: Int
    val centerPointRadius: Int
    val progressBarRadius: Int
    val progressBarWidth: Int
    val arrowDistance: Int

    val carIcon: Bitmap
    val atFrontArrow: Bitmap
    val sector: Bitmap

    val carIconBoundsColor: Int
    val progressBarUsedColor: Int
    val progressBarUnusedColor: Int

    val viewSize: Size = Size(0, 0)

    init {
        context.obtainStyledAttributes(attrs, R.styleable.CarNavigationView, R.attr.carNavigationViewStyle, R.style.DefaultNavigationViewStyle).use {
            carIconSize = it.getDimensionPixelSize(R.styleable.CarNavigationView_carIconSize, 0)
            carIconBgRadius = it.getDimensionPixelSize(R.styleable.CarNavigationView_carIconBgRadius, 0)
            centerPointRadius = it.getDimensionPixelSize(R.styleable.CarNavigationView_centerPointRadius, 0)
            progressBarRadius = it.getDimensionPixelSize(R.styleable.CarNavigationView_progressBarRadius, 0)
            progressBarWidth = it.getDimensionPixelSize(R.styleable.CarNavigationView_progressBarWidth, 0)
            arrowDistance = it.getDimensionPixelSize(R.styleable.CarNavigationView_arrowDistance, 0)

            carIcon = it.getDrawable(R.styleable.CarNavigationView_carIcon)!!.toBitmap()
            atFrontArrow = it.getDrawable(R.styleable.CarNavigationView_atFrontArrow)!!.toBitmap()
            sector = it.getDrawable(R.styleable.CarNavigationView_sector)!!.toBitmap()

            carIconBoundsColor = it.getColor(R.styleable.CarNavigationView_carIconBoundsColor, 0)
            progressBarUsedColor = it.getColor(R.styleable.CarNavigationView_progressBarUsedColor, 0)
            progressBarUnusedColor = it.getColor(R.styleable.CarNavigationView_progressBarUnusedColor, 0)
        }
    }
}

interface NavigationState {

    val naviView: CarNavigationView

    var progress: Float

    fun initParams()

    fun onProgressChanged()

    fun display()

    fun onEnterState()

    fun onExitState()
}

sealed class BaseState(override val naviView: CarNavigationView) : NavigationState {

    override var progress: Float = 0f
        set(value) {
            if (field != value) {
                field = value
                onProgressChanged()
            }
            field = value
        }

    protected val ac: AnimConfiguration
        get() = naviView.ac

    override fun onProgressChanged() {
        // do nothing
    }

    override fun display() {
        naviView.invalidate()
    }

    override fun onEnterState() {
        // do nothing
    }

    override fun onExitState() {
        // do nothing
    }

}

class SearchState(naviView: CarNavigationView) : BaseState(naviView) {

    override fun initParams() {
        naviView.apply {
            frontSector.updateParams {
                alpha = 1f
            }
            emptyProgressBar.updateParams {
                alpha = 0.1f
                scale = 1f
            }
            iconCar.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat())
            }
            iconCarBackground.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat())
            }
            carWaterWave.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat())
            }
            carArrow.updateParams {
                polarPosition.set(ac.arrowDistance.toFloat(), -90f)
            }
        }
    }

    override fun onProgressChanged() {
        val initialAngle = -90 + progress * 360f
        naviView.iconCar.updateParams {
            polarPosition.set(ac.progressBarRadius.toFloat(), initialAngle)
        }
        naviView.iconCarBackground.updateParams {
            polarPosition.set(ac.progressBarRadius.toFloat(), initialAngle)
        }
        naviView.carWaterWave.updateParams {
            polarPosition.set(ac.progressBarRadius.toFloat(), initialAngle)
        }
        naviView.progressBar.updateParams {
            polarPosition.set(ac.progressBarRadius.toFloat(), initialAngle)
        }
        display()
    }

    override fun onExitState() {
        naviView.carWaterWave.stop()
        naviView.carArrow.stopAnim()
    }

    override fun onEnterState() {
        naviView.carArrow.startFadeAnim(true)
    }
}

internal class AtFrontState(naviView: CarNavigationView) : BaseState(naviView) {

    override fun initParams() {
        naviView.apply {
            frontSector.updateParams {
                alpha = 1f
            }
            emptyProgressBar.updateParams {
                alpha = 0.1f
                scale = 1f
            }
            iconCar.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat(), -90f)
                scale = 1f
                alpha = 1f
            }
            iconCarBackground.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat(), -90f)
                scale = 1f
                alpha = 1f
            }
            carWaterWave.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat(), -90f)
                scale = 1f
                alpha = 1f
            }
            progressBar.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat(), -90f)
            }
        }
    }

    override fun onEnterState() {
        naviView.carWaterWave.start(0.2f, 1.6f)
        naviView.carArrow.startFadeAnim(false)
    }

    override fun onExitState() {
        naviView.carWaterWave.stop()
        naviView.carArrow.stopAnim()
    }

}

class NearbyState(override val naviView: CarNavigationView) : BaseState(naviView) {

    override var progress: Float = 0f

    private val anims = mutableListOf<AnimatorSet>()

    override fun initParams() {
        naviView.apply {
            progressBar.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat(), -90f)
            }
            frontSector.updateParams {
                alpha = 0f
            }
        }
    }

    override fun onEnterState() {
        startIntermediateAnim(false)
        naviView.carArrow.startFadeAnim(false)
    }

    private fun startIntermediateAnim(reverse: Boolean) {
        naviView.carWaterWave.updateParams {
            polarPosition.set(0f)
        }
        val translationAnim = ValueAnimator.ofFloat(0f, ac.progressBarRadius.toFloat()).apply {
            duration = 600
            interpolator = alphaInterpolator
        }
        val fadeOutAnim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 600
            interpolator = alphaInterpolator
        }
        val emptyProgressBarEnlargeAnim = ValueAnimator.ofFloat(1f, 1.4f).apply {
            duration = 600
            interpolator = alphaInterpolator
        }
        val carBackgroundEnlargeAnim = ValueAnimator.ofFloat(1f, 3.7f).apply {
            duration = 600
            interpolator = alphaInterpolator
        }

        translationAnim.addUpdateListener {
            val translationY = it.animatedValue as Float
            naviView.iconCar.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat() - translationY)
            }
            naviView.iconCarBackground.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat() - translationY)
            }
            naviView.carWaterWave.updateParams {
                polarPosition.set(ac.progressBarRadius.toFloat() - translationY)
            }
            naviView.invalidate()
        }
        fadeOutAnim.addUpdateListener {
            val targetAlpha = it.animatedValue as Float
            naviView.iconCar.updateParams {
                alpha = targetAlpha
            }
            naviView.frontSector.updateParams {
                alpha = targetAlpha
            }
            naviView.invalidate()
        }
        emptyProgressBarEnlargeAnim.addUpdateListener {
            val targetScale = it.animatedValue as Float
            naviView.emptyProgressBar.updateParams {
                scale = targetScale
            }
        }
        carBackgroundEnlargeAnim.addUpdateListener {
            val targetScale = it.animatedValue as Float
            naviView.iconCarBackground.updateParams {
                scale = targetScale
            }
            naviView.carWaterWave.updateParams {
                scale = targetScale
            }
            naviView.invalidate()
        }
        val animSet = AnimatorSet()
        animSet.doOnStart {
            naviView.carWaterWave.stop()
            naviView.isDoingIntermediateAnim.set(true)
            anims.add(animSet)
        }
        animSet.doOnEnd {
            naviView.isDoingIntermediateAnim.set(false)
            anims.remove(animSet)
        }
        animSet.playTogether(
            translationAnim,
            fadeOutAnim,
            emptyProgressBarEnlargeAnim,
            carBackgroundEnlargeAnim
        )
        if (reverse) {
            animSet.reverse()
            stopNearbyAnim()
        } else {
            animSet.start()
            startNearbyAnim()
        }
    }

    private fun startNearbyAnim() {
        naviView.carWaterWave.start(1f, 1.92f)
    }

    private fun stopNearbyAnim() {
        naviView.carWaterWave.stop()
    }

    override fun onExitState() {
        startIntermediateAnim(true)
    }

}

internal interface ProgressController {
    var progress: Float
}

private val scaleInterpolator = PathInterpolatorCompat.create(0f, 0f, 0.52f, 1f)
private val alphaInterpolator = PathInterpolatorCompat.create(0.33f, 0f, 0.67f, 1f)

private interface UIPart {

    val context: Context

    val paint: Paint

    var params: UIParams

    fun updateParams(block: UIParams.() -> Unit) {
        params.apply(block)
        onParamsChanged()
    }

    fun onParamsChanged()

    fun draw(canvas: Canvas)

    fun onDimensChanged()
}

internal interface IAnimation {
    fun start(vararg params: Any)
    fun stop()
}

internal open class BaseUIPart(override val context: Context, protected val ac: AnimConfiguration) :
    UIPart {

    override var params: UIParams = UIParams(PolarPosition(0f, 0f))
        set(value) {
            field = value
            onParamsChanged()
        }

    override val paint = Paint()

    private val cp = CartesianPosition()

    protected fun getCartesianPosition(): CartesianPosition {
        return params.polarPosition.toCartesian(cp)
            .offset(ac.viewSize.width / 2f, ac.viewSize.height / 2f)
    }

    override fun onParamsChanged() {
        paint.alpha = (params.alpha * 255).toInt()
    }

    override fun draw(canvas: Canvas) {
        // do nothing
    }

    override fun onDimensChanged() {
        // do nothing
    }

}

internal data class UIParams(
    val polarPosition: PolarPosition,
    var rotation: Float = 0f,
    var scale: Float = 1f,
    var alpha: Float = 1f
)

internal data class PolarPosition(
    var radius: Float,
    var angle: Float
)

private fun PolarPosition.set(radius: Float = this.radius, angle: Float = this.angle) {
    this.radius = radius
    this.angle = angle
}

internal data class CartesianPosition(
    var x: Float = 0f,
    var y: Float = 0f
)

internal fun CartesianPosition.set(x: Float = this.x, y: Float = this.y): CartesianPosition {
    this.x = x
    this.y = y
    return this
}

data class Size(
    var width: Int,
    var height: Int
) {
    fun set(width: Int, height: Int) {
        this.width = width
        this.height = height
    }
}

/**
 * 极坐标转换为直角坐标
 * angle 是角度，需要先转化为弧度
 */
private fun PolarPosition.toCartesian(cartesianPosition: CartesianPosition): CartesianPosition{
    val x = radius * cos(Math.toRadians(angle.toDouble())).toFloat()
    val y = radius * sin(Math.toRadians(angle.toDouble())).toFloat()
    return cartesianPosition.set(x, y)
}

private fun CartesianPosition.offset(offsetX: Float, offsetY: Float): CartesianPosition {
    return set(x + offsetX, y + offsetY)
}

internal class CenterPoint(override val context: Context, ac: AnimConfiguration) :
    BaseUIPart(context, ac) {

    override val paint: Paint = Paint().apply {
        color = ac.carIconBoundsColor
        style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val cartesianPosition = getCartesianPosition()
        canvas.drawCircle(
            cartesianPosition.x,
            cartesianPosition.y,
            ac.centerPointRadius.toFloat(),
            paint
        )
    }

}

internal class FrontSector(override val context: Context, ac: AnimConfiguration) :
    BaseUIPart(context, ac) {

    override fun draw(canvas: Canvas) {
        val cartesianPosition =
            getCartesianPosition().offset(-ac.sector.width / 2f, -ac.sector.height.toFloat())
        canvas.drawBitmap(ac.sector, cartesianPosition.x, cartesianPosition.y, paint)
    }

}

internal class EmptyProgressBar(override val context: Context, ac: AnimConfiguration) :
    BaseUIPart(context, ac) {

    override val paint: Paint = Paint().apply {
        color = ac.progressBarUnusedColor
        style = Paint.Style.STROKE
        strokeWidth = ac.progressBarWidth.toFloat()
    }

    /**
     * 绘制圆环
     * 半径为：dimens.progressBarRadius
     * 宽度为：dimens.progressBarWidth
     */
    override fun draw(canvas: Canvas) {
        val cartesianPosition = getCartesianPosition()
        canvas.drawCircle(
            cartesianPosition.x,
            cartesianPosition.y,
            ac.progressBarRadius.toFloat() * params.scale,
            paint
        )
    }

}

internal class ProgressBar(override val context: Context, ac: AnimConfiguration) :
    BaseUIPart(context, ac) {

    override val paint: Paint = Paint().apply {
        color = ac.progressBarUsedColor
        style = Paint.Style.STROKE
        strokeWidth = ac.progressBarWidth.toFloat()
        strokeCap = Paint.Cap.ROUND
    }
    private val progressRect = RectF()

    override fun onDimensChanged() {
        progressRect.set(
            (ac.viewSize.width - ac.progressBarRadius * 2) / 2f,
            (ac.viewSize.height - ac.progressBarRadius * 2) / 2f,
            (ac.viewSize.width + ac.progressBarRadius * 2) / 2f,
            (ac.viewSize.height + ac.progressBarRadius * 2) / 2f
        )
    }

    override fun draw(canvas: Canvas) {
        val sweepAngles = transformAngleToSweepAngle(params.polarPosition.angle)
        canvas.drawArc(
            progressRect,
            sweepAngles.first,
            sweepAngles.second,
            false,
            paint
        )
    }

    private fun transformAngleToSweepAngle(angle: Float): Pair<Float, Float> {
        return if (angle > 90) {
            Pair(angle, 270 - angle)
        } else {
            Pair(-90f, angle + 90)
        }
    }

}

internal class IconCar(override val context: Context, ac: AnimConfiguration) :
    BaseUIPart(context, ac) {

    override fun draw(canvas: Canvas) {
        val cartesianPosition =
            getCartesianPosition().offset(-ac.carIcon.width / 2f, -ac.carIcon.height / 2f)
        canvas.drawBitmap(ac.carIcon, cartesianPosition.x, cartesianPosition.y, paint)
    }

}

internal class IconCarBackground(override val context: Context, ac: AnimConfiguration) :
    BaseUIPart(context, ac) {

    override val paint: Paint = Paint().apply {
        color = ac.carIconBoundsColor
    }

    override fun draw(canvas: Canvas) {
        val cartesianPosition = getCartesianPosition()
        canvas.drawCircle(
            cartesianPosition.x,
            cartesianPosition.y,
            ac.carIconBgRadius.toFloat() * params.scale,
            paint
        )
    }

}

internal class CarArrow(override val context: Context, ac: AnimConfiguration) :
    BaseUIPart(context, ac) {

    private var fadeAnim: ValueAnimator? = null

    override fun draw(canvas: Canvas) {
        val cartesianPosition =
            getCartesianPosition().offset(-ac.atFrontArrow.width / 2f, -ac.atFrontArrow.height / 2f)
        canvas.drawBitmap(
            ac.atFrontArrow,
            cartesianPosition.x,
            cartesianPosition.y,
            paint
        )
    }

    fun startFadeAnim(fadeIn: Boolean) {
        fadeAnim?.cancel()

        if ((fadeIn && params.alpha == 1f) ||
            (!fadeIn && params.alpha == 0f)) return

        fadeAnim = if (fadeIn) {
            ValueAnimator.ofFloat(0f, 1f)
        } else {
            ValueAnimator.ofFloat(1f, 0f)
        }.apply {
            duration = 650
            interpolator = alphaInterpolator
            addUpdateListener {
                val alpha = it.animatedValue as Float
                params.alpha = alpha
                onParamsChanged()
            }
            start()
        }
    }

    fun stopAnim() {
        fadeAnim?.cancel()
    }

}

internal class CarWaterWave(
    override val context: Context,
    ac: AnimConfiguration,
    private val naviView: CarNavigationView
) :
    BaseUIPart(context, ac), IAnimation {
    override var params: UIParams = UIParams(PolarPosition(0f, 0f))
        set(value) {
            field = value
            onParamsChanged()
        }

    private val waves = mutableListOf<WaveParams>()

    private val enabled = AtomicBoolean(false)
    private val anims = mutableListOf<AnimatorSet>()
    private val handler = Handler(Looper.getMainLooper())
    override fun draw(canvas: Canvas) {
        val cartesianPosition = getCartesianPosition()
        waves.toList().forEach {
            canvas.drawCircle(cartesianPosition.x, cartesianPosition.y, it.radius, it.paint)
        }
    }

    data class WaveParams(
        val paint: Paint = Paint(),
        var radius: Float = 0f,
        // 模糊
    )

    override fun start(vararg params: Any) {
        if (anims.isNotEmpty()) return
        enabled.set(true)
        require(params.size == 2)
        startInternal(params[0] as Float, params[1] as Float)
    }

    private fun startInternal(scaleStart: Float, scaleEnd: Float) {
        if (enabled.get().not()) return
        val wave = WaveParams(
            Paint().apply {
                color = ac.carIconBoundsColor
                style = Paint.Style.FILL
            },
            radius = ac.carIconBgRadius.toFloat()
        )
        val enlargeAnim = createEnlargeAnim(scaleStart, scaleEnd)
        enlargeAnim.addUpdateListener {
            val scale = it.animatedValue as Float
            wave.radius = ac.carIconBgRadius * params.scale * scale
            naviView.invalidate()
        }
        val fadeInAnim = createFadeInAnim()
        fadeInAnim.addUpdateListener {
            val alpha = it.animatedValue as Float
            wave.paint.alpha = (alpha * 255).toInt()
            naviView.invalidate()
        }
        val fadeOutAnim = createFadeOutAnim()
        fadeOutAnim.addUpdateListener {
            val alpha = it.animatedValue as Float
            wave.paint.alpha = (alpha * 255).toInt()
            naviView.invalidate()
        }
        val blurAnim = createBlurAnim()
        blurAnim.addUpdateListener {
            val alpha = it.animatedValue as Float
            wave.paint.maskFilter =
                BlurMaskFilter(ac.carIconBgRadius * alpha, BlurMaskFilter.Blur.NORMAL)
        }
        val animSet = AnimatorSet()
        animSet.doOnEnd {
            waves.remove(wave)
            anims.remove(animSet)
        }
        animSet.doOnStart {
            waves.add(wave)
            anims.add(animSet)
        }
        animSet.playTogether(enlargeAnim, fadeInAnim, fadeOutAnim)
        animSet.start()

        handler.postDelayed({ startInternal(scaleStart, scaleEnd) }, 1000)
    }

    private fun createEnlargeAnim(scaleStart: Float, scaleEnd: Float): ValueAnimator {
        return ValueAnimator.ofFloat(scaleStart, scaleEnd).apply {
            duration = 4500
            interpolator = scaleInterpolator
        }
    }

    private fun createFadeInAnim(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 0.2f).apply {
            duration = 500
            interpolator = alphaInterpolator
        }
    }

    private fun createFadeOutAnim(): ValueAnimator {
        return ValueAnimator.ofFloat(0.2f, 0f).apply {
            duration = 2500
            interpolator = alphaInterpolator
            startDelay = 2000
        }
    }

    private fun createBlurAnim(): ValueAnimator {
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2500
            interpolator = alphaInterpolator
            startDelay = 2000
        }
    }

    override fun stop() {
        enabled.set(false)
        waves.clear()
        anims.toList().forEach(AnimatorSet::cancel)
        handler.removeCallbacksAndMessages(null)
    }

}