package recycler.stacklayout

class ItemLayoutInfo {
    var scaleXY //缩放比例
            = 0f

    var left = 0

    var alpha = 1.0f

    constructor(left: Int, scaleXY: Float) {
        this.left = left
        this.scaleXY = scaleXY
    }
}