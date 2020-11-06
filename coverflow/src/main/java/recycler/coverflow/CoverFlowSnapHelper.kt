package recycler.coverflow

import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper
import recycler.stacklayout.StackLayoutManager

class CoverFlowSnapHelper: SnapHelper() {

    private var realScrollPos = 0
    private var enableLog = true

    private var enableLoop = true

    override fun calculateDistanceToFinalSnap(
        layoutManager: RecyclerView.LayoutManager,
        targetView: View
    ): IntArray? {
        if (layoutManager is CoverFlowLayoutManger3) {
            val out = IntArray(2)
            var pos = -1
            if (layoutManager.canScrollHorizontally()) {
                pos = layoutManager.getPosition(targetView)
                if (enableLoop) {
                    pos = realScrollPos
                }
                out[0] = layoutManager.calculateOffsetForPosition(
                    pos
                )
            } else {
                out[0] = 0
            }
            out[1] = 0
            if (enableLog) {
                Log.i(
                    StackLayoutManager.TAG,
                    " snapHelper calculateDistanceToFinalSnap, out[0]: " + out[0] + ", pos: " + pos
                )
            }
            return out
        }
        return null
    }

    override fun findSnapView(layoutManager: RecyclerView.LayoutManager?): View? {
        if (layoutManager is CoverFlowLayoutManger3) {
            val pos = layoutManager.getFixedScrollPosition()
            var realPos = pos
            realScrollPos = pos
            val itemCount = layoutManager.itemCount
            if (enableLoop && itemCount > 0) {
                realPos = pos % itemCount
                // 循环滚动时，位置可能是负值，需要将其转换为对应的 item 的值
                if (realPos < 0) {
                    realPos += itemCount
                }
            }
            if (enableLog) {
                Log.i(
                    StackLayoutManager.TAG,
                    " snapHelper findSnapView, pos: $pos, realPos: $realPos"
                )
            }
            if (pos != RecyclerView.NO_POSITION) {
                val snapView = layoutManager.findViewByPosition(realPos)
                Log.i(
                    StackLayoutManager.TAG,
                    " snapHelper findSnapView, snapView null ? " + (snapView == null)
                )
                return snapView
            }
        }
        return null
    }

    override fun findTargetSnapPosition(
        layoutManager: RecyclerView.LayoutManager?,
        velocityX: Int,
        velocityY: Int
    ): Int {
        return RecyclerView.NO_POSITION
    }

    companion object {
        const val TAG = "CoverFlow_" + "SnapHelper"
    }
}