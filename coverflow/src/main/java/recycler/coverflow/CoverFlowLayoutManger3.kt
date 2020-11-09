package recycler.coverflow

import android.animation.Animator
import android.animation.ValueAnimator
import android.graphics.*
import android.util.Log
import android.util.SparseArray
import android.util.SparseBooleanArray
import android.util.SparseIntArray
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.RecyclerView
import recycler.stacklayout.StackSnapHelper
import kotlin.math.abs
import kotlin.math.roundToInt

class CoverFlowLayoutManger3(
    isFlat: Boolean, isGreyItem: Boolean,
    isAlphaItem: Boolean, cstInterval: Float,
    isLoop: Boolean, is3DItem: Boolean
) : RecyclerView.LayoutManager()/*,RecyclerView.SmoothScroller.ScrollVectorProvider*/ {
    /**滑动总偏移量 */
    private var mOffsetAll = 0

    /**Item宽 */
    private var mDecoratedChildWidth = 0

    /**Item高 */
    private var mDecoratedChildHeight = 0

    /**Item间隔与item宽的比例 */
    private var mIntervalRatio: Float = 0.5f
        get() {
//            return if(getItemWidth() >= 0) {
//                intervalDistance * 1.0f / getItemWidth()
//            }else {
//                0.5f
//            }
            return 0.8f
        }

    private var snapHelper: CoverFlowSnapHelper? = null

    /**起始ItemX坐标 */
    private var mStartX = 0

    /**起始Item Y坐标 */
    private var mStartY = 0

    /**保存所有的Item的上下左右的偏移量信息 */
    private val mAllItemFrames = SparseArray<Rect>()

    /**记录Item是否出现过屏幕且还没有回收。true表示出现过屏幕上，并且还没被回收 */
    private val mHasAttachedItems = SparseBooleanArray()

    val mActualPosition2AdapterPosition = SparseIntArray()

    val mActualPos2LayoutPos = SparseIntArray()

    /**RecyclerView的Item回收器 */
    private var mRecycle: RecyclerView.Recycler? = null

    /**RecyclerView的状态器 */
    private var mState: RecyclerView.State? = null

    /**滚动动画 */
    private var mAnimation: ValueAnimator? = null
    /**
     * 获取被选中Item位置
     */
    /**正显示在中间的Item */
    var selectedPos = 0
        private set

    /**前一个正显示在中间的Item */
    private var mLastSelectPosition = 0

    /**选中监听 */
    private var mSelectedListener: OnItemScrollListener? = null

    /**是否为平面滚动，Item之间没有叠加，也没有缩放 */
    private var mIsFlatFlow = false

    /**是否启动Item灰度值渐变 */
    private var mItemGradualGrey = false

    /**是否启动Item半透渐变 */
    private var mItemGradualAlpha = false

    /**是否无限循环 */
    private var mIsLoop = false

    /**是否启动Item 3D 倾斜 */
    private var mItem3D = false

    var recyclerView: RecyclerView? = null

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun getItemWidth(): Int {
        return horizontalSpace - (MAX_COUNT - 1) * intervalDistance
    }

    private fun getItemHeight(): Int {
        return verticalSpace
    }

//    override fun computeScrollVectorForPosition(targetPosition: Int): PointF? {
//        if (childCount == 0) {
//            return null
//        }
//        val firstChildPos = getPosition(getChildAt(0)!!)
//        val distancePos = targetPosition - firstChildPos
//        val direction = if (targetPosition < firstChildPos) {
//            // start
//            if (itemCount > 2 * abs(distancePos)) -1 else 1
//        } else {
//            // end
//            if (itemCount > 2 * abs(distancePos)) -1 else 1
//        }
//        return PointF(direction.toFloat(), 0f)
//    //    val direction = if (targetPosition < firstChildPos) -1 else 1
//    //    return PointF(direction.toFloat(), 0f)
//
//    }

    override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
        //如果没有item，直接返回
        //跳过preLayout，preLayout主要用于支持动画
        if (itemCount <= 0 || state.isPreLayout) {
            mOffsetAll = 0
            return
        }
        mAllItemFrames.clear()
        mHasAttachedItems.clear()
        //计算测量布局的宽高
        mDecoratedChildWidth = getItemWidth()
        mDecoratedChildHeight = getItemHeight()

        if(mDecoratedChildWidth <= 0) {
            return
        }

        mStartX = (MAX_COUNT / 2) * intervalDistance  //最中间那个为起始item
        mStartY = 0

        Log.i(TAG, " onLayoutChildren, itemWidth: $mDecoratedChildWidth, startX: $mStartX ")

        detachAndScrapAttachedViews(recycler) //在布局之前，将所有的子View先Detach掉，放入到Scrap缓存中
        //首次时才需要回调
        if ((mRecycle == null || mState == null)) {  //在为初始化前调用smoothScrollToPosition 或者 scrollToPosition,只会记录位置
            mOffsetAll = calculateOffsetForPosition(selectedPos)          //所以初始化时需要滚动到对应位置
        }
        layoutItems(recycler, state, SCROLL_TO_LEFT)
        if(mRecycle == null || mState == null) {
            onSelectedCallBack()
        }
        mRecycle = recycler
        mState = state
    }

    override fun scrollHorizontallyBy(
        dx: Int, recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        // 手指从右向左滑动，dx > 0; 手指从左向右滑动，dx < 0;
        if (mAnimation?.isRunning == true) mAnimation?.cancel()
        var travel = dx
        if (!mIsLoop) { //非循环模式，限制滚动位置
            if (dx + mOffsetAll < 0) {
                travel = -mOffsetAll
            } else if (dx + mOffsetAll > maxOffset) {
                travel = (maxOffset - mOffsetAll).toInt()
            }
        }
        Log.i(TAG, " scrollHorizontallyBy dx: $dx, offsetAll: $mOffsetAll ")

        mOffsetAll += travel //累计偏移量
        layoutItems(recycler, state, if (dx > 0) SCROLL_TO_LEFT else SCROLL_TO_RIGHT)
        return travel
    }

    private fun layoutItems2(recycler: RecyclerView.Recycler?, state: RecyclerView.State?, scrollDirection: Int) {

        if (state == null || state.isPreLayout) return


        val position = centerPosition

        // 检查前后 20 个 item 是否需要绘制
        var min = position - 10
        var max = position + 10


        for(i in min until max) {
            val rect = getFrame(i)

            var actualPos = i % itemCount
            // 循环滚动时，位置可能是负值，需要将其转换为对应的 item 的值
            if (actualPos < 0) actualPos += itemCount
            val scrap = recycler!!.getViewForPosition(actualPos)
            checkTag(scrap.tag)
            scrap.tag = TAG(i)
            measureChildWithMargins(scrap, 0, 0)
            if (scrollDirection == SCROLL_TO_RIGHT || mIsFlatFlow) { //item 向右滚动，新增的Item需要添加在最前面
                addView(scrap, 0)
            } else { //item 向左滚动，新增的item要添加在最后面
                addView(scrap)
            }
            layoutItem(scrap, rect) //将这个Item布局出来
        }
    }


    /**
     * 布局Item
     *
     *
     * 1，先清除已经超出屏幕的item
     *
     * 2，再绘制可以显示在屏幕里面的item
     */
    private fun layoutItems(
        recycler: RecyclerView.Recycler?,
        state: RecyclerView.State?, scrollDirection: Int
    ) {
        if (state == null || state.isPreLayout) return

        val displayFrame = Rect(mOffsetAll, 0, mOffsetAll + horizontalSpace, verticalSpace)
        var position = 0
        val scrollState = recyclerView?.scrollState
        Log.i(TAG, " layoutItems , offsetAll: $mOffsetAll, childCount: $childCount, " +
                "displayFrame: $displayFrame , width: ${displayFrame.width()}, ChildWidth: $mDecoratedChildWidth, scrollState: $scrollState")

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            position = if (child.tag != null) {
                val tag = checkTag(child.tag)
                tag?.pos?:0
            } else {
                getPosition(child)
            }
            val rect = getFrame(position)
            if (!Rect.intersects(displayFrame, rect)) { //Item没有在显示区域，就说明需要回收
                removeAndRecycleView(child, recycler!!) //回收滑出屏幕的View
                mHasAttachedItems.delete(position)
                mActualPosition2AdapterPosition.delete(position)
                Log.i(TAG, " layoutItems, removeAndRecycleView, position: $position, rect: $rect ")
            } else { //Item还在显示区域内，更新滑动后Item的位置
                layoutItem(child, rect) //更新Item位置
                Log.i(TAG, " layoutItem, updateLayout position: $position, rect: $rect ")
                mHasAttachedItems.put(position, true)
//                var actualPos = i % itemCount
//                mActualPosition2AdapterPosition.put(position, actualPos)
            }
        }

        position = centerPosition

        // 检查前后 20 个 item 是否需要绘制
        var min = position - 10
        var max = position + 10

        if (!mIsLoop) {
            if (min < 0) min = 0
            if (max > itemCount) max = itemCount
        }

//        for(i in min until position) {
//            addLayoutView(i, displayFrame, recycler)
//        }
//
//        for (i in max downTo  position + 1) {
//            Log.i(TAG, " downTo test i: $i ")
//            addLayoutView(i, displayFrame, recycler)
//        }
//
//        addLayoutView(position, displayFrame, recycler)

        for (i in min until max) {
            addLayoutView(i, displayFrame, recycler, scrollDirection)
        }
        if(scrollState != RecyclerView.SCROLL_STATE_IDLE) {
            mSelectedListener?.onItemScrolled()
        }
    }

    private fun addLayoutView(i: Int, displayFrame: Rect,   recycler: RecyclerView.Recycler?, scrollDirection: Int) {
        val rect = getFrame(i)
        if (Rect.intersects(displayFrame, rect) &&
            !mHasAttachedItems[i]
        ) { //重新加载可见范围内的Item
            // 循环滚动时，计算实际的 item 位置
            var actualPos = i % itemCount
            // 循环滚动时，位置可能是负值，需要将其转换为对应的 item 的值
            if (actualPos < 0) actualPos += itemCount
            val scrap = recycler!!.getViewForPosition(actualPos)
            checkTag(scrap.tag)
            scrap.tag = TAG(i)
            measureChildWithMargins(scrap, 0, 0)
            if (scrollDirection == SCROLL_TO_RIGHT || mIsFlatFlow) { //item 向右滚动，新增的Item需要添加在最前面
                addView(scrap, 0)
            } else { //item 向左滚动，新增的item要添加在最后面
                addView(scrap)
            }
            layoutItem(scrap, rect) //将这个Item布局出来
            mActualPosition2AdapterPosition.put(i, actualPos)
            Log.i(TAG, " layoutItem, addView rect: $rect , i: $i, actualPos: $actualPos")
            mHasAttachedItems.put(i, true)
        }
    }

    /**
     * 布局Item位置
     * @param child 要布局的Item
     * @param frame 位置信息
     */
    private fun layoutItem(child: View?, frame: Rect) {
        child?.apply {
            val scaleXY = computeScale(frame.left - mOffsetAll)
            val scaledWidth = frame.width() * scaleXY
            val scaleWidth = frame.width() - scaledWidth
            var left = frame.left - mOffsetAll

            val scaledHeight = frame.height() * scaleXY
            val scaleHeight = frame.height() - scaledHeight
            if(left >= mStartX) {
                left += (scaleWidth/2).toInt()
            }else {
                left -= (scaleWidth/2).toInt()
            }

            val top = frame.top + (scaleHeight/2).toInt()
            layoutDecorated(
                this,
                left,
                top,
                left + frame.width(),
                top + frame.height()
            )

            if (!mIsFlatFlow) { //不是平面普通滚动的情况下才进行缩放
                scaleX = scaleXY //缩放
                scaleY = scaleXY //缩放
            }
            if (mItemGradualAlpha) {
                alpha = computeAlpha(frame.left - mOffsetAll)
            }
            if (mItemGradualGrey) {
                greyItem(this, frame)
            }
            if (mItem3D) {
                item3D(this, frame)
            }
        }
    }

    /**
     * 动态获取Item的位置信息
     * @param index item位置
     * @return item的Rect信息
     */
    private fun getFrame(index: Int): Rect {
        var frame = mAllItemFrames[index]
        if (frame == null) {
            frame = Rect()
            val offset = mStartX + intervalDistance * index.toFloat() //原始位置累加（即累计间隔距离）
            frame[offset.roundToInt(), mStartY, (offset + mDecoratedChildWidth).roundToInt()] =
                mStartY + mDecoratedChildHeight
        }
        return frame
    }

    /**
     * 变化Item的灰度值
     * @param child 需要设置灰度值的Item
     * @param frame 位置信息
     */
    private fun greyItem(child: View?, frame: Rect) {
        val value = computeGreyScale(frame.left - mOffsetAll)
        val cm = ColorMatrix(
            floatArrayOf(
                value,
                0f,
                0f,
                0f,
                120 * (1 - value),
                0f,
                value,
                0f,
                0f,
                120 * (1 - value),
                0f,
                0f,
                value,
                0f,
                120 * (1 - value),
                0f,
                0f,
                0f,
                1f,
                250 * (1 - value)
            )
        )
        //        cm.setSaturation(0.9f);

        // Create a paint object with color matrix
        val greyPaint = Paint()
        greyPaint.colorFilter = ColorMatrixColorFilter(cm)

        // Create a hardware layer with the grey paint
        child!!.setLayerType(View.LAYER_TYPE_HARDWARE, greyPaint)
        if (value >= 1) {
            // Remove the hardware layer
            child.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }

    private fun item3D(child: View?, frame: Rect) {
        val center = (frame.left + frame.right - 2 * mOffsetAll) / 2f
        var value =
            (center - (mStartX + mDecoratedChildWidth / 2f)) * 1f / (itemCount * intervalDistance)
        value = Math.sqrt(Math.abs(value).toDouble()).toFloat()
        val symbol = if (center > mStartX + mDecoratedChildWidth / 2f) -1 else 1
        child!!.rotationY = symbol * 50 * value
    }

    var hasScrolled = false;
    override fun onScrollStateChanged(state: Int) {
        super.onScrollStateChanged(state)
        when (state) {
            RecyclerView.SCROLL_STATE_IDLE -> {
               fixOffsetWhenFinishScroll()  //滚动停止时
            }
            RecyclerView.SCROLL_STATE_DRAGGING -> {
                hasScrolled = true
            }
            RecyclerView.SCROLL_STATE_SETTLING -> {
                hasScrolled = true
            }
        }
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        super.onLayoutCompleted(state)
    }

    override fun scrollToPosition(position: Int) {
        if (position < 0 || position > itemCount - 1) return
        mOffsetAll = calculateOffsetForPosition(getChildActualPos(position))
        if (mRecycle == null || mState == null) { //如果RecyclerView还没初始化完，先记录下要滚动的位置
            selectedPos = position
        } else {
            layoutItems(
                mRecycle,
                mState,
                if (position > selectedPos) SCROLL_TO_LEFT else SCROLL_TO_RIGHT
            )
            onSelectedCallBack()
        }
        Log.i(TAG, "scrollToPosition, position: $position  ")
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {
        // TODO 循环模式暂不支持平滑滚动
        if (mIsLoop) return
        val finalOffset = calculateOffsetForPosition(position)
        if (mRecycle == null || mState == null) { //如果RecyclerView还没初始化完，先记录下要滚动的位置
            selectedPos = position
        } else {
            startScroll(mOffsetAll, finalOffset)
        }
    }

    override fun canScrollHorizontally(): Boolean {
        return true
    }

    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?
    ) {
        removeAllViews()
        mRecycle = null
        mState = null
        mOffsetAll = 0
        selectedPos = 0
        mLastSelectPosition = 0
        mHasAttachedItems.clear()
        mAllItemFrames.clear()
    }

    /**
     * 获取整个布局的水平空间大小
     */
    private val horizontalSpace: Int
        get() = width - paddingRight - paddingLeft

    /**
     * 获取整个布局的垂直空间大小
     */
    private val verticalSpace: Int
        get() = height - paddingBottom - paddingTop

    /**
     * 获取最大偏移量
     */
    private val maxOffset: Float
        get() = ((itemCount - 1) * intervalDistance).toFloat()

    /**
     * 计算Item缩放系数
     * @param x Item的x坐标
     * @return 缩放系数
     */
    private fun computeScale(x: Int): Float {
//        var scale =
//            1 - abs(x - mStartX) * 1.0f / abs(mStartX + mDecoratedChildWidth / mIntervalRatio)
//
//        if (scale < 0) scale = 0f
//        if (scale > 1) scale = 1f
        //距离最中间的第几个view，每个view height被缩放的距离相等，因此可以获得该view被缩放的高度
        val scaledHeight = (abs(x - mStartX)* 1.0f / intervalDistance) * intervalHeightDistance
        val scale =  (getItemHeight() - scaledHeight) / getItemHeight()

        return scale
    }

    /**
     * 计算Item的灰度值
     * @param x Item的偏移量
     * @return 灰度系数
     */
    private fun computeGreyScale(x: Int): Float {
        val itemMidPos = x + mDecoratedChildWidth / 2.toFloat() //item中点x坐标
        val itemDx2Mid = Math.abs(itemMidPos - horizontalSpace / 2f) //item中点距离控件中点距离
        var value = 1 - itemDx2Mid * 1.0f / (horizontalSpace / 2)
        if (value < 0.1) value = 0.1f
        if (value > 1) value = 1f
        value = Math.pow(value.toDouble(), .8).toFloat()
        return value
    }

    /**
     * 计算Item半透值
     * @param x Item的偏移量
     * @return 缩放系数
     */
    private fun computeAlpha(x: Int): Float {
        var alpha =
            1 - Math.abs(x - mStartX) * 1.0f / Math.abs(mStartX + mDecoratedChildWidth / mIntervalRatio)

        alpha = computeScale(x)
        alpha = alpha*alpha
        if (alpha < 0.1f) alpha = 0.1f
        if (alpha > 1) alpha = 1.0f
        return alpha
    }

    /**
     * 计算Item所在的位置偏移, 相对于中间的距离
     * @param position 要计算Item位置
     */
     fun calculateOffsetForPosition(position: Int): Int {
        return (intervalDistance * position.toFloat()).roundToInt()
    }

    /**
     * 修正停止滚动后，Item滚动到中间位置
     */
     fun fixOffsetWhenFinishScroll() {
        if (intervalDistance != 0) { // 判断非 0 ，否则除 0 会导致异常
            var scrollN = (mOffsetAll * 1.0f / intervalDistance).toInt()
            val moreDx = (mOffsetAll % intervalDistance).toFloat()
            if (abs(moreDx) > intervalDistance * 0.5) {
                if (moreDx > 0) scrollN++ else scrollN--
            }
            val finalOffset = scrollN * intervalDistance
            startScroll(mOffsetAll, finalOffset)
        //    selectedPos = abs((finalOffset * 1.0f / intervalDistance).roundToInt()) % itemCount
        }
    }


    fun getFixedScrollPosition(): Int {
        if (intervalDistance != 0) { // 判断非 0 ，否则除 0 会导致异常
            var scrollN = (mOffsetAll * 1.0f / intervalDistance).toInt()
            val moreDx = (mOffsetAll % intervalDistance).toFloat()
            if (abs(moreDx) > intervalDistance * 0.5) {
                if (moreDx > 0) scrollN++ else scrollN--
            }
            return scrollN
        }else {
            return RecyclerView.NO_POSITION
        }
    }

    /**
     * 滚动到指定X轴位置
     * @param from X轴方向起始点的偏移量
     * @param to X轴方向终点的偏移量
     */
    private fun startScroll(from: Int, to: Int) {
        if (mAnimation?.isRunning == true) {
            mAnimation?.cancel()
        }
        Log.i(TAG, " startScroll, from: $from, to: $to ")
        val direction = if (from < to) SCROLL_TO_LEFT else SCROLL_TO_RIGHT
        mAnimation = ValueAnimator.ofFloat(from.toFloat(), to.toFloat())
        mAnimation?.apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener(ValueAnimator.AnimatorUpdateListener { animation ->
                mOffsetAll = (animation.animatedValue as Float).roundToInt()
                layoutItems(mRecycle, mState, direction)
            })
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationEnd(animation: Animator) {
                    onSelectedCallBack()
                }

                override fun onAnimationCancel(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            start()
        }
    }

    /**
     * 获取Item间隔
     */
    private val intervalDistance: Int
        private get() = 150

    private val intervalHeightDistance: Int = 75

    /**
     * 计算当前选中位置，并回调
     */
    private fun onSelectedCallBack() {
        selectedPos = (mOffsetAll / intervalDistance.toFloat()).roundToInt()
        val adapterPos = mActualPosition2AdapterPosition.get(selectedPos, -10000)
        var realSelected = selectedPos % itemCount
        if(realSelected < 0) {
            realSelected += itemCount
        }
        if (mSelectedListener != null && (selectedPos == 0 || selectedPos != mLastSelectPosition || hasScrolled)) {
            mSelectedListener!!.onItemSelected(realSelected)
            Log.i(TAG, " onSelectedCallBack, selectedPos: $selectedPos, realSelected: $realSelected, adapterPos: $adapterPos, firstPos: $firstVisiblePosition, lastPos: $lastVisiblePosition ")
        }
        hasScrolled = false;
        mLastSelectPosition = selectedPos
    }

    private fun checkTag(tag: Any?): TAG? {
        return if (tag != null) {
            if (tag is TAG) {
                tag
            } else {
                throw IllegalArgumentException("You should not use View#setTag(Object tag), use View#setTag(int key, Object tag) instead!")
            }
        } else {
            null
        }
    }

    /**
     * 获取第一个可见的Item位置
     *
     * Note:该Item为绘制在可见区域的第一个Item，有可能被第二个Item遮挡
     */
    val firstVisiblePosition: Int
        get() {
            val displayFrame = Rect(mOffsetAll, 0, mOffsetAll + horizontalSpace, verticalSpace)
            val cur = centerPosition
            var i = cur - 1
            while (true) {
                val rect = getFrame(i)
                if (rect.left <= displayFrame.left) {
                //    return abs(i) % itemCount
                    var realPos = i % itemCount
                    if(realPos < 0) {
                        realPos += itemCount
                    }
                    return realPos
                }
                i--
            }
        }

    /**
     * 获取最后一个可见的Item位置
     *
     * Note:该Item为绘制在可见区域的最后一个Item，有可能被倒数第二个Item遮挡
     */
    val lastVisiblePosition: Int
        get() {
            val displayFrame = Rect(mOffsetAll, 0, mOffsetAll + horizontalSpace, verticalSpace)
            val cur = centerPosition
            var i = cur + 1
            while (true) {
                val rect = getFrame(i)
                if (rect.right >= displayFrame.right) {
                    return abs(i) % itemCount
                }
                i++
            }
        }

    /**
     * 该方法主要用于[RecyclerCoverFlow.getChildDrawingOrder]判断中间位置，主要是获取相对centerPosition的位置
     * @param index child 在 RecyclerCoverFlow 中的位置
     * @return child 的实际位置，如果 [.mIsLoop] 为 true ，返回结果可能为负值， 初始时centerPosition为0，则靠近center的依次为-1，-2，-3，右边为1，2，3，如果有滑动会依次累加
     */
    fun getChildActualPos(index: Int): Int {
        val child = getChildAt(index)
        return if (child!!.tag != null) {
            val tag = checkTag(child.tag)
            tag!!.pos
        } else {
            val position = getPosition(child)
            Log.i(TAG, " getPosition, index: $index , position: $position")
            position
        }
    }

    /**
     * 获取可见范围内最大的显示Item个数
     */
    val maxVisibleCount: Int
        get() {
            val oneSide = (horizontalSpace - mStartX) / intervalDistance
            return oneSide * 2 + 1
        }

    /**
     * 获取中间位置
     *
     * Note:该方法主要用于[RecyclerCoverFlow.getChildDrawingOrder]判断中间位置
     *
     * 如果需要获取被选中的Item位置，调用[.getSelectedPos]
     */
    val centerPosition: Int
        get() {
            var pos = mOffsetAll / intervalDistance
            val more = mOffsetAll % intervalDistance
            if (abs(more) >= intervalDistance * 0.5f) {
                if (more >= 0) pos++ else pos--
            }
            return pos
        }

    /**
     * 设置选中监听
     * @param l 监听接口
     */
    fun setOnSelectedListener(l: OnItemScrollListener?) {
        mSelectedListener = l
    }

    override fun onAttachedToWindow(view: RecyclerView?) {
        super.onAttachedToWindow(view)
    //    view?.onFlingListener = null
    //    snapHelper?.attachToRecyclerView(view)
    }

    /**
     * 选中监听接口
     */
    interface OnItemScrollListener {
        /**
         * 监听选中回调
         * @param position 显示在中间的Item的位置
         */
        fun onItemSelected(position: Int)

        fun onItemScrolled()
    }

    inner class TAG internal constructor(var pos: Int)
    internal class Builder {
        var isFlat = false
        var isGreyItem = false
        var isAlphaItem = false
        var cstIntervalRatio = -1f
        var isLoop = false
        var is3DItem = false
        fun setFlat(flat: Boolean): Builder {
            isFlat = flat
            return this
        }

        fun setGreyItem(greyItem: Boolean): Builder {
            isGreyItem = greyItem
            return this
        }

        fun setAlphaItem(alphaItem: Boolean): Builder {
            isAlphaItem = alphaItem
            return this
        }

        fun setIntervalRatio(ratio: Float): Builder {
            cstIntervalRatio = ratio
            return this
        }

        fun loop(): Builder {
            isLoop = true
            return this
        }

        fun set3DItem(d3: Boolean): Builder {
            is3DItem = true
            return this
        }

        fun build(): CoverFlowLayoutManger3 {
            return CoverFlowLayoutManger3(
                isFlat, isGreyItem,
                isAlphaItem, cstIntervalRatio, isLoop, is3DItem
            )
        }
    }

    companion object {
        /**item 向右移动 */
        private const val SCROLL_TO_RIGHT = 1

        /**item 向左移动 */
        private const val SCROLL_TO_LEFT = 2

        private const val MAX_COUNT = 5
        /**
         * 最大存储item信息存储数量，
         * 超过设置数量，则动态计算来获取
         */
        private const val MAX_RECT_COUNT = 100
        const val TAG = "CoverFlow_" + "LayoutManager"
    }

    init {
        mIsFlatFlow = isFlat
        mItemGradualGrey = isGreyItem
        mItemGradualAlpha = isAlphaItem
        mIsLoop = isLoop
        mItem3D = is3DItem
        if (cstInterval >= 0) {
            mIntervalRatio = cstInterval
        } else {
            if (mIsFlatFlow) {
                mIntervalRatio = 1.1f
            }
        }
        snapHelper = CoverFlowSnapHelper()
    }

}