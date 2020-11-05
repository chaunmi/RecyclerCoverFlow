package recycler.stacklayout

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Recycler
import java.util.*

class StackLayoutManager : RecyclerView.LayoutManager {

    private var enableLog = false

    var maxCount //屏幕上最多显示多少个itemView
            = 0
    var itemSpace = 24 //每个itemView之间的间隔空隙

    private var stackSnapHelper: StackSnapHelper? = null
    var scrollOffset //向左滑动的总距离
            = 0
    var hasInit //初始化参数
            = false
    var itemWidth = 0
    var itemHeight = 0
    var currentItemCount = 0

    //循环滚动
    var enableLoop = true
    private var mItemScrollListeners: MutableList<OnItemScrollListener>? = null
    private var recyclerView: RecyclerView? = null

    var scale = floatArrayOf(0.8957f, 0.776f, 0.625f) //前两个是根据视觉算出来的，最后一个视觉没给，自己填的

    var alpha = floatArrayOf(0.5f, 0.15f, 0.05f)
    var lastTopItemScrollWidth = 0
    var lastSelectedItemPosition = RecyclerView.NO_POSITION

    var top2BottomLayoutPosition: IntArray? = null

    //是否真正的手动滚动过
    var hasStartScrolled = false
    private var isFirstLayout = true


    constructor(
        maxCount: Int,
        itemSpace: Int,
        recyclerView: RecyclerView?,
        enableLoop: Boolean
    ) {
        this.maxCount = maxCount
        this.itemSpace = itemSpace
        this.enableLoop = enableLoop
        stackSnapHelper = StackSnapHelper(enableLoop)
        stackSnapHelper?.setEnableLog(true)
        this.recyclerView = recyclerView
    }

    fun setEnableLog(enableLog: Boolean) {
        this.enableLog = enableLog
    }

    override fun onItemsChanged(recyclerView: RecyclerView) {
        super.onItemsChanged(recyclerView)
    }

    fun resetParams() {
        hasStartScrolled = false
        hasInit = false
        lastSelectedItemPosition = RecyclerView.NO_POSITION
        top2BottomLayoutPosition = null
        isFirstLayout = true
        scrollOffset = 0
        lastTopItemScrollWidth = 0
        requestLayout()
    }

    /**
     *
     * 手动开始滑动时的回调顺序 首先onItemScrollStateChanged，然后onItemScrolled
     * 最后的回调顺序，然后onItemScrolled， 然后首先onItemScrollStateChanged， 最后onItemSelected（如果选中的有变动）
     */
    interface OnItemScrollListener {
        /**
         *
         * @param recyclerView
         * @param recycler
         * @param totalScrollOffset   当前item累计滑动的距离，永远为正
         * @param currentPosition   当前滚动的是哪个position
         * @param currentScrollOffset  跟随手势当次实际滚动的距离,带方向，从右向左 < 0, 从左向右 > 0
         * @param currentItemScrollPercent   最上面用户滑动的item的百分比
         * @param top2BottomLayoutPosition  向左划出屏幕时（从右向左一次完整滑动或者从左往右不完整滑动）包含了划出屏幕的item，
         * 向右滑进屏幕时（从左向右一次完整滑动或者从右向左不完整滑动）则为当前页面看到的各个item
         */
        fun onItemScrolled(
            recyclerView: RecyclerView,
            recycler: Recycler?,
            totalScrollOffset: Int,
            currentPosition: Int,
            currentScrollOffset: Int,
            currentItemScrollPercent: Float,
            top2BottomLayoutPosition: IntArray?
        )

        fun onItemScrollStateChanged(state: Int, position: Int)

        /**
         *
         * @param position  当前最顶部选中的item
         * @param top2BottomLayoutPosition  向左划出屏幕时（从右向左一次完整滑动或者从左往右不完整滑动）包含了划出屏幕的item，
         * 向右滑进屏幕时（从左向右一次完整滑动或者从右向左不完整滑动）则为当前页面看到的各个item
         */
        fun onItemSelected(position: Int, top2BottomLayoutPosition: IntArray?)
    }

    fun addOnItemScrollListener(listener: OnItemScrollListener) {
        if (mItemScrollListeners == null) {
            mItemScrollListeners = ArrayList()
        }
        mItemScrollListeners!!.add(listener)
    }

    fun removeOnItemScrollListener(listener: OnItemScrollListener) {
        if (mItemScrollListeners != null) {
            mItemScrollListeners!!.remove(listener)
        }
    }


    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams? {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun onLayoutChildren(recycler: Recycler, state: RecyclerView.State) {
        if (state.itemCount == 0) {
            removeAndRecycleAllViews(recycler)
            return
        }
        if (state.isPreLayout) {
            return
        }
        initParams()
        updateScrollOffset(scrollOffset)
        onLayout(recycler, state, 0)
    }

    private fun initParams() {
        if (!hasInit || itemWidth == 0 || currentItemCount == 0) {  //初始化相关参数
            currentItemCount = itemCount
            hasInit = true
        }
    }

    private fun checkItemReady(): Boolean {
        if (itemWidth == 0 || currentItemCount == 0) {
            initParams()
            if (itemWidth == 0 || currentItemCount == 0) {
                return false
            }
        }
        return true
    }

    /**
     *
     * @param recycler
     * @param state
     * @param currentScrollOffset  跟随手势当次实际滚动的距离,带方向，从右向左 < 0, 从左向右 > 0
     */
    private fun onLayout(recycler: Recycler, state: RecyclerView.State, currentScrollOffset: Int) {
        if (!checkItemReady()) {
            return
        }
        var topItemPosition = Math.floor(getCurrentPosition().toDouble()).toInt()
        if (topItemPosition < 0) {
            return
        }
        var topItemScrollWidth =
            scrollOffset % itemWidth //最顶部那个item当前滑动的距离, 当滑动完的时候scrollOffset正好为itemWidth的倍数，topItemScrollWidth即为0
        if (currentScrollOffset != 0 && !hasStartScrolled) {
            hasStartScrolled = true
        }
        //已经不再滑动了,只有完成一次完整切换的时候才会有滑动完之后再来一次currentScrollOffset为0的情况，否则滑动之后还是当前item则不会有为0的情况
        if (hasStartScrolled) {
            if (currentScrollOffset == 0) {
                hasStartScrolled = false
            }
        }
        //修复从右向左滑动且为一次完整的滑动或者从左向右滑动且不为一次完整滑动（即没有最终滑动最右边）的时候，即最终结果是向左划出屏幕的情况，修正最后的滑动值
        // 因为此时topItemScrollWidth为0，但实际上滑动距离应该为itemWidth的宽度
        if (topItemScrollWidth == 0 && lastTopItemScrollWidth != itemWidth) {
            if (Math.abs(itemWidth - lastTopItemScrollWidth) <= 10) {
                topItemScrollWidth = itemWidth
                topItemPosition = topItemPosition - 1
            }
        }
        lastTopItemScrollWidth = topItemScrollWidth
        val topItemScrollPercent = topItemScrollWidth * 1.0f / itemWidth //移动的百分比
        //    float remainPercent = 1 - topItemScrollPercent;  //剩余待移动的百分比

        //开始计算每个itemView的位置和缩放比例等数据
        var left = -1 * topItemScrollWidth //最顶部item滑动缩放不变
        var scaleXY = 1.0f
        var itemAlpha = 1.0f
        val layoutInfos: MutableList<ItemLayoutInfo> = ArrayList()
        var itemLayoutInfo = ItemLayoutInfo(left, scaleXY)
        layoutInfos.add(itemLayoutInfo)
        var lastScaleXY = scaleXY
        var itemPositionIndex = topItemPosition + 1
        var lastAlpha = itemAlpha
        run {
            var i = 1
            while (i < maxCount + 1) {
                if (!enableLoop) {
                    if (itemPositionIndex >= currentItemCount) {
                        break
                    }
                }
                val baseItemScale = scale[i - 1]
                val baseItemSpace = (i - 1) * itemSpace
                val baseAlpha = alpha[i - 1]
                val diffScaleXY = lastScaleXY - baseItemScale
                val diffAlpha = lastAlpha - baseAlpha
                scaleXY =
                    baseItemScale + diffScaleXY * topItemScrollPercent //实际缩放的比例，根据第一个item的拖动比例来计算
                itemAlpha = baseAlpha + diffAlpha * topItemScrollPercent
                left =
                    baseItemSpace + (itemSpace * (1 - topItemScrollPercent)).toInt() //如果以往中心缩放来算，X和Y都向中心缩放
                //     Log.i(TAG, " onLayout , i: " + i + ", scaleItem: " + baseItemScale +  ", scaleXY: " + scaleXY + ", topItemScrollPercent:" + topItemScrollPercent + ",scrollOffset: " + scrollOffset + ",topItemPosition: " + topItemPosition);
                itemLayoutInfo = ItemLayoutInfo(left, scaleXY)
                itemLayoutInfo.alpha = itemAlpha
                layoutInfos.add(itemLayoutInfo)
                lastScaleXY = baseItemScale
                lastAlpha = baseAlpha
                i++
                itemPositionIndex++
            }
        }
        val layoutCount = layoutInfos.size
        //        final int firstInvisiblePos = topItemPosition;
//        final int lastInvisiblePos = topItemPosition + layoutCount;
        //view回收处理

        //先进行回收
        detachAndScrapAttachedViews(recycler)
        //再进行重新布局
        var childView: View? = null
        for (i in layoutCount - 1 downTo 0) {
            val adapterPos = topItemPosition + i
            var realAdapterPos = adapterPos
            if (enableLoop && currentItemCount > 0) {
                realAdapterPos = realAdapterPos % currentItemCount
            }
            val stateItemCount = state.itemCount
            if (realAdapterPos < stateItemCount) {
                try {
                    childView = recycler.getViewForPosition(realAdapterPos)
                    if (childView != null && childView.getMeasuredWidth() != 0) {
                        if (top2BottomLayoutPosition == null) {
                            top2BottomLayoutPosition = IntArray(layoutCount)
                        }
                    }
                    if (top2BottomLayoutPosition != null) {
                        top2BottomLayoutPosition!![i] = realAdapterPos
                    }
                    val itemLayoutInfo1 = layoutInfos[i]
                    layoutChild(childView, itemLayoutInfo1) //从最右往左一个view一个view的加
                } catch (e: Exception) {
                    Log.e(TAG, " layout error: ", e)
                }
            }
            if (enableLog) {
                Log.i(
                    TAG, " onLayout , layoutChild, i: " + i + ",realAdapterPos: " + realAdapterPos +
                            ", adapterPos: " + adapterPos + ", currentItemCount: " + currentItemCount + ", stateItemCount: " + stateItemCount
                            + ",top2BottomPosition: " + Arrays.toString(top2BottomLayoutPosition)
                )
            }
        }
        if (mItemScrollListeners != null && hasStartScrolled && top2BottomLayoutPosition != null) {
            if (enableLoop && currentItemCount > 0) {
                topItemPosition = topItemPosition % currentItemCount
            }
            for (i in mItemScrollListeners!!.indices.reversed()) {
                mItemScrollListeners!![i].onItemScrolled(
                    recyclerView!!,
                    recycler,
                    topItemScrollWidth,
                    topItemPosition,
                    currentScrollOffset,
                    topItemScrollPercent,
                    top2BottomLayoutPosition
                )
            }
        }
        if (enableLog) {
            Log.i(
                TAG,
                " onLayout scrollOffset: " + scrollOffset + ",hasStartScrolled: " + hasStartScrolled +
                        ", topItemScrollWidth: " + topItemScrollWidth + ", topItemPosition: " + topItemPosition +
                        ", currentScrollOffset: " + currentScrollOffset + ", lastTopItemScrollWidth: " + lastTopItemScrollWidth +
                        ", scrollState: " + recyclerView!!.scrollState + ", topItemScrollPercent: " + topItemScrollPercent
            )
        }

        //已经滑动完毕,补一下无完整滑动的时的情况
        if (hasStartScrolled) {
            if (topItemScrollWidth == 0) {
                hasStartScrolled = false
            }
        }
    }

//    private boolean isFloatEqual(float value,  float compareValue){
//        if(Math.abs(value - compareValue) < 0.0000001f){
//            return true;
//        }else{
//            return false;
//        }
//    }


    //    private boolean isFloatEqual(float value,  float compareValue){
    //        if(Math.abs(value - compareValue) < 0.0000001f){
    //            return true;
    //        }else{
    //            return false;
    //        }
    //    }
    private fun layoutChild(view: View, itemLayoutInfo: ItemLayoutInfo) {
        addView(view)
        measureChildWithExactlySize(view)
        val scaleItem = itemLayoutInfo.scaleXY
        val scaleWidth = (itemWidth * scaleItem).toInt() //缩放之后的宽度
        //    int scaleHeight = (int)(itemHeight * scaleItem); //缩放之后的高度
        val scaledWidth = itemWidth - scaleWidth //宽度被缩放了多少
        //    int scaledHeight = itemHeight - scaleHeight; //高度被缩放了多少
        val left = scaledWidth / 2 + itemLayoutInfo.left
        val top = paddingTop
        val right = left + itemWidth
        val bottom = top + itemHeight
        //再layout
        layoutDecoratedWithMargins(view, left, top, right, bottom)
        view.scaleX = scaleItem
        view.scaleY = scaleItem
        view.alpha = itemLayoutInfo.alpha
    }

    private fun relayout(recycler: Recycler, state: RecyclerView.State, realScrollOffset: Int) {
        onLayout(recycler, state, realScrollOffset)
    }

    /**
     *
     * @param targetPos  adapter position
     * @return
     */
    fun calculateDistanceToPosition(targetPos: Int): Int {
        return targetPos * itemWidth - scrollOffset
    }

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)
        view.onFlingListener = null
        stackSnapHelper?.attachToRecyclerView(view)
    }

    override fun onDetachedFromWindow(view: RecyclerView?, recycler: Recycler?) {
        super.onDetachedFromWindow(view, recycler)
    }

    override fun scrollHorizontallyBy(dx: Int, recycler: Recycler, state: RecyclerView.State): Int {
        // 手指从右向左滑动，dx > 0; 手指从左向右滑动，dx < 0;
        val pendingScrollOffset = scrollOffset + dx
        val originalScrollOffset = scrollOffset
        updateScrollOffset(pendingScrollOffset)
        val realOffset = originalScrollOffset - scrollOffset
        relayout(recycler, state, realOffset)
        return realOffset //返回实际滑动了多少距离
    }

    override fun onScrollStateChanged(state: Int) {
        if (!checkItemReady()) {
            return
        }
        var topItemPosition = Math.floor(getCurrentPosition().toDouble()).toInt()
        var realPosition = topItemPosition
        if (currentItemCount > 0) {
            realPosition = topItemPosition % currentItemCount
        }
        var offset = scrollOffset
        if (itemWidth > 0) {
            offset = scrollOffset % itemWidth
        }
        if (mItemScrollListeners != null) {
            if (enableLoop && currentItemCount > 0) {
                topItemPosition = topItemPosition % currentItemCount
            }
            var realState = state
            for (i in mItemScrollListeners!!.indices.reversed()) {
                if (realState == RecyclerView.SCROLL_STATE_IDLE && offset != 0) {
                    realState = RecyclerView.SCROLL_STATE_SETTLING
                }
                mItemScrollListeners!![i].onItemScrollStateChanged(realState, topItemPosition)
                if (offset == 0 && state == RecyclerView.SCROLL_STATE_IDLE && realPosition != lastSelectedItemPosition) {
                    mItemScrollListeners!![i].onItemSelected(realPosition, top2BottomLayoutPosition)
                    lastSelectedItemPosition = realPosition
                    if (enableLog) {
                        Log.i(
                            TAG,
                            " onScrollStateChanged: state $state, realState: $realState,topItemPosition:  $topItemPosition,realPosition: $realPosition,offset: $offset, realScrollOffset: $scrollOffset"
                        )
                    }
                }
            }
        }
    }

    override fun onLayoutCompleted(state: RecyclerView.State?) {
        if (!checkItemReady()) {
            return
        }
        val topItemPosition = Math.floor(getCurrentPosition().toDouble()).toInt()
        var realPosition = topItemPosition
        if (enableLoop && currentItemCount > 0) {
            realPosition = topItemPosition % currentItemCount
        }
        if (lastSelectedItemPosition != realPosition && isFirstLayout && top2BottomLayoutPosition != null) {
            if (mItemScrollListeners != null) {
                for (i in mItemScrollListeners!!.indices.reversed()) {
                    mItemScrollListeners!![i].onItemSelected(realPosition, top2BottomLayoutPosition)
                }
            }
            lastSelectedItemPosition = realPosition
            isFirstLayout = false
        }
        Log.i(
            TAG,
            " onScrollStateChanged onLayoutCompleted: topItemPosition:  $topItemPosition,realPosition: $realPosition"
        )
    }

    override fun scrollToPosition(position: Int) { //adapter position
        if (position >= 0 && position < currentItemCount) {
            scrollOffset = itemWidth * position
            requestLayout()
        }
    }

    override fun canScrollHorizontally(): Boolean {
        return if (currentItemCount == 1) {
            false
        } else {
            true
        }
    }

    private fun measureChildWithExactlySize(child: View) {
        val lp = child.layoutParams as RecyclerView.LayoutParams
        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            itemWidth - lp.leftMargin - lp.rightMargin, View.MeasureSpec.EXACTLY
        )
        val heightSpec = View.MeasureSpec.makeMeasureSpec(
            itemWidth - lp.topMargin - lp.bottomMargin, View.MeasureSpec.EXACTLY
        )
        child.measure(widthSpec, heightSpec)
    }

    //实际能滑动的距离
    private fun updateScrollOffset(offset: Int): Int {
        scrollOffset = if (enableLoop) {
            Math.max(offset, 0)
        } else {
            Math.min(Math.max(offset, 0), (currentItemCount - 1) * itemWidth)
        }
        return scrollOffset
    }

    //获取当前最前面显示的item的position
    private fun getCurrentPosition(): Float {
        return scrollOffset * 1.0f / itemWidth
    }

    private var lastScrollOffset = 0
    private var fixedPosition = RecyclerView.NO_POSITION
    var fixedValue = 0.8f

    var close_98 = 0.98f
    var close_02 = 0.02f
    fun getFixedScrollPosition(): Int {
        if (hasInit) {
            if (!checkItemReady()) {
                return RecyclerView.NO_POSITION
            }
            val itemPosition = getCurrentPosition()
            val positionInt = itemPosition.toInt()
            val diff = itemPosition - positionInt
            var nowReturn = false
            var nowReturnPosition = RecyclerView.NO_POSITION
            if (scrollOffset % itemWidth == 0) {
                lastScrollOffset = scrollOffset
                nowReturnPosition = positionInt
                nowReturn = true
            } else if (diff.compareTo(close_98) > 0) {  //修正快速滑动时不会滑动itemWidth整数倍的问题
                lastScrollOffset = (positionInt + 1) * itemWidth
                nowReturnPosition = positionInt + 1
                nowReturn = true
            } else if (diff.compareTo(close_02) < 0) {  //修正快速滑动时不会滑动itemWidth整数倍的问题
                lastScrollOffset = positionInt * itemWidth
                nowReturnPosition = positionInt
                nowReturn = true
            }
            if (nowReturn) {
                if (enableLog) {
                    Log.i(
                        TAG,
                        " snapHelper, getfixedScrollPosition_1, scrollOffset: " + scrollOffset + ", lastScrollOffset: " + lastScrollOffset
                                + ", fixedPosition: " + fixedPosition + ", itemPosition: " + itemPosition + ", nowReturnPosition: " + nowReturnPosition
                    )
                }
                fixedPosition = RecyclerView.NO_POSITION
                return nowReturnPosition
            }
            if (fixedPosition == RecyclerView.NO_POSITION) {
                if (scrollOffset > lastScrollOffset) {  //从右向左滑动比例超过20%，则向左滑走，否则向右滑动回来
                    fixedPosition = (itemPosition + fixedValue).toInt()
                    if (enableLog) {
                        Log.i(
                            TAG,
                            " snapHelper, getfixedScrollPosition_2, scrollOffset: " + scrollOffset + ", lastScrollOffset: " + lastScrollOffset + ", itemPosition: "
                                    + itemPosition + ", fixedPosition: " + fixedPosition
                        )
                    }
                } else { //从左向右滑动
                    val float1 = lastScrollOffset * 1.0f / itemWidth - 1.0f
                    val scrollPercent = itemPosition - float1
                    fixedPosition =
                        if (scrollPercent.compareTo(fixedValue) < 0) {  //从左往右滑动比例超过20%， 则往右滑动过来
                            float1.toInt()
                        } else {  //否则向左反弹回去
                            float1.toInt() + 1
                        }
                    if (enableLog) {
                        Log.i(
                            TAG,
                            " snapHelper, getfixedScrollPosition_3, scrollOffset: " + scrollOffset + ", lastScrollOffset: " + lastScrollOffset + ", itemPosition: "
                                    + itemPosition + ", fixedPosition: " + fixedPosition + ",float1: " + float1
                        )
                    }
                }
            }
            return fixedPosition
        }
        return RecyclerView.NO_POSITION
    }

    fun getVerticalSpace(): Int {
        return height - paddingTop - paddingBottom
    }

    fun getHorizontalSpace(): Int {
        return width - paddingLeft - paddingRight
    }

    companion object {
        val TAG = "StackLayout"
    }

}