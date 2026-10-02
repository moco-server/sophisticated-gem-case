package org.mocoserver.modtemplate.core

/** 負の数量を拒否し、ゲーム側へ渡せる数量を返す。 */
public fun checkedQuantity(value: Int): Int {
    require(value >= 0) { "Quantity must be non-negative" }
    return value
}
