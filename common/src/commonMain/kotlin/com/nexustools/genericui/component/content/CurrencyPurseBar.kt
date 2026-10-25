package com.nexustools.genericui.component.content

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.typography.Label
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Multi-denomination RPG currency display and budget tracker (`Gold`, `Silver`, `Copper` from a
 * base decimal gold value where `1 Gold = 10 Silver = 100 Copper`), rendering `.currency.gold`,
 * `.currency.silver`, and `.currency.copper` badges (matching `DCGame`'s `CharGenStep8`).
 */
open class CurrencyPurseBar(
    initialPurseGold: Double = 0.0
) : HStack() {

    val goldLabel: Label = Label("0", "span", "currency-value")
    val goldBadge: Container = Container("span", "currency gold me-2")
    val silverLabel: Label = Label("0", "span", "currency-value")
    val silverBadge: Container = Container("span", "currency silver me-2")
    val copperLabel: Label = Label("0", "span", "currency-value")
    val copperBadge: Container = Container("span", "currency copper")

    var totalPurseGold: Double = initialPurseGold
        set(value) {
            field = value.coerceAtLeast(0.0)
            syncLabels()
        }

    var spentGold: Double = 0.0
        set(value) {
            field = value.coerceAtLeast(0.0)
            syncLabels()
        }

    val remainingGold: Double
        get() = (totalPurseGold - spentGold).coerceAtLeast(0.0)

    val goldCoins: Int
        get() = decompose(remainingGold).first

    val silverCoins: Int
        get() = decompose(remainingGold).second

    val copperCoins: Int
        get() = decompose(remainingGold).third

    init {
        htmlTag = "genericui:currencypursebar"
        addClass("currency-purse")
        addChild(goldLabel)
        addChild(goldBadge)
        addChild(silverLabel)
        addChild(silverBadge)
        addChild(copperLabel)
        addChild(copperBadge)
        syncLabels()
    }

    fun canAfford(costGold: Double): Boolean =
        (costGold * 100.0).roundToInt() <= (remainingGold * 100.0).roundToInt()

    private fun syncLabels() {
        val (g, s, c) = decompose(remainingGold)
        goldLabel.text = g.toString()
        silverLabel.text = s.toString()
        copperLabel.text = c.toString()
    }

    companion object {
        fun decompose(amountGold: Double): Triple<Int, Int, Int> {
            val totalCopper = (amountGold.coerceAtLeast(0.0) * 100.0).roundToInt()
            val gold = totalCopper / 100
            val silver = (totalCopper % 100) / 10
            val copper = totalCopper % 10
            return Triple(gold, silver, copper)
        }

        fun formatBreakdown(amountGold: Double): String {
            val (g, s, c) = decompose(amountGold)
            return "${g}g ${s}s ${c}c"
        }
    }
}
