package recycler.coverflow

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class CoverFlowRecyclerView : RecyclerView {

    /**
     * 按下的X轴坐标
     */
    private var mDownX = 0f
    private var mDownY = 0f

    /**
     * 布局器构建者
     */
    private var mManagerBuilder: CoverFlowLayoutManger3.Builder? = null

    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(
        context!!, attrs
    ) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context!!, attrs, defStyle
    ) {
        init()
    }

    private fun init() {
        createManageBuilder()
        layoutManager = mManagerBuilder!!.build()
        isChildrenDrawingOrderEnabled = true //开启重新排序
        overScrollMode = View.OVER_SCROLL_NEVER
        coverFlowLayout?.recyclerView = this
    }

    /**
     * 创建布局构建器
     */
    private fun createManageBuilder() {
        if (mManagerBuilder == null) {
            mManagerBuilder = CoverFlowLayoutManger3.Builder()
        }
    }

    /**
     * 设置是否为普通平面滚动
     * @param isFlat true:平面滚动；false:叠加缩放滚动
     */
    fun setFlatFlow(isFlat: Boolean) {
        createManageBuilder()
        mManagerBuilder!!.setFlat(isFlat)
        layoutManager = mManagerBuilder!!.build()
    }

    /**
     * 设置Item灰度渐变
     * @param greyItem true:Item灰度渐变；false:Item灰度不变
     */
    fun setGreyItem(greyItem: Boolean) {
        createManageBuilder()
        mManagerBuilder!!.setGreyItem(greyItem)
        layoutManager = mManagerBuilder!!.build()
    }

    /**
     * 设置Item灰度渐变
     * @param alphaItem true:Item半透渐变；false:Item透明度不变
     */
    fun setAlphaItem(alphaItem: Boolean) {
        createManageBuilder()
        mManagerBuilder!!.setAlphaItem(alphaItem)
        layoutManager = mManagerBuilder!!.build()
    }

    /**
     * 设置无限循环滚动
     */
    fun setLoop() {
        createManageBuilder()
        mManagerBuilder!!.loop()
        layoutManager = mManagerBuilder!!.build()
    }

    /**
     * 设置Item 3D 倾斜
     * @param d3 true：Item 3d 倾斜；false：Item 正常摆放
     */
    fun set3DItem(d3: Boolean) {
        createManageBuilder()
        mManagerBuilder!!.set3DItem(d3)
        layoutManager = mManagerBuilder!!.build()
    }

    /**
     * 设置Item的间隔比例
     * @param intervalRatio Item间隔比例。
     * 即：item的宽 x intervalRatio
     */
    fun setIntervalRatio(intervalRatio: Float) {
        createManageBuilder()
        mManagerBuilder!!.setIntervalRatio(intervalRatio)
        layoutManager = mManagerBuilder!!.build()
    }

    override fun setLayoutManager(layout: LayoutManager?) {
        require(layout is CoverFlowLayoutManger) { "The layout manager must be CoverFlowLayoutManger" }
        super.setLayoutManager(layout)
    }

    /**
     * 获取LayoutManger，并强制转换为CoverFlowLayoutManger
     */
    val coverFlowLayout: CoverFlowLayoutManger3?
        get() = layoutManager as CoverFlowLayoutManger3?

    /**
     * 获取被选中的Item位置
     */
    val selectedPos: Int
        get() = coverFlowLayout!!.selectedPos

    /**
     * 设置选中监听
     * @param l 监听接口
     */
    fun setOnItemSelectedListener(l: CoverFlowLayoutManger3.OnItemScrollListener?) {
        coverFlowLayout!!.setOnSelectedListener(l)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                mDownX = ev.x
                mDownY = ev.y
                parent.requestDisallowInterceptTouchEvent(true) //设置父类不拦截滑动事件
            }
            MotionEvent.ACTION_MOVE -> if (ev.x > mDownX && coverFlowLayout!!.centerPosition == 0 ||
                ev.x < mDownX && coverFlowLayout!!.centerPosition ==
                coverFlowLayout!!.itemCount - 1
            ) {
                //如果是滑动到了最前和最后，开放父类滑动事件拦截
                parent.requestDisallowInterceptTouchEvent(false)
            } else {
                //滑动到中间，设置父类不拦截滑动事件
                if (abs(ev.x - mDownX) > abs(ev.y - mDownY)) {
                    parent.requestDisallowInterceptTouchEvent(true)
                } else {
                    parent.requestDisallowInterceptTouchEvent(false)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        const val TAG = "CoverFlow_" + "Recycler"
    }
}