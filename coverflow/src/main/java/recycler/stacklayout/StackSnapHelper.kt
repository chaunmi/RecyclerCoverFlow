package recycler.stacklayout

import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SnapHelper

class StackSnapHelper: SnapHelper {
    private var enableLog = false

    private var enableLoop = false
    private var realScrollPos = 0

    constructor(enableLoop: Boolean) {
        this.enableLoop = enableLoop
    }

    fun setEnableLog(enableLog: Boolean) {
        this.enableLog = enableLog
    }

    override fun calculateDistanceToFinalSnap(
        layoutManager: RecyclerView.LayoutManager,
        targetView: View
    ): IntArray? {
        if (layoutManager is StackLayoutManager) {
            val out = IntArray(2)
            var pos = -1
            if (layoutManager.canScrollHorizontally()) {
                pos = layoutManager.getPosition(targetView)
                if (enableLoop) {
                    pos = realScrollPos
                }
                out[0] = layoutManager.calculateDistanceToPosition(
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

    override fun findSnapView(layoutManager: RecyclerView.LayoutManager): View? {
        if (layoutManager is StackLayoutManager) {
            val stackLayoutManager = layoutManager
            val pos = stackLayoutManager.getFixedScrollPosition()
            var realPos = pos
            realScrollPos = pos
            val itemCount = stackLayoutManager.itemCount
            if (enableLoop && itemCount > 0) {
                realPos = pos % itemCount
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
}