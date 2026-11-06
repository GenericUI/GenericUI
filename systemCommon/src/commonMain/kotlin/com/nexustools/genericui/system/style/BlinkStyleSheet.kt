package com.nexustools.genericui.system.style

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.style.css.CompiledStyleRule
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.css.PrecompiledStyleSheet

/**
 * Base class for build-time precompiled `.kt` stylesheets targeting `:systemCommon` backends.
 *
 * Uses [BlinkRuleBucketMap] + [BlinkStyleEngine] so stylesheets can be attached directly to an
 * [IApplication] with zero runtime CSS or SASS string parsing.
 */
abstract class BlinkStyleSheet : PrecompiledStyleSheet {
    abstract val ruleSet: BlinkRuleBucketMap

    override val rawCss: String
        get() = ""

    override val compiledRules: List<CompiledStyleRule> by lazy {
        ruleSet.allRules.map { it.toCompiledStyleRule() }
    }

    override val styleSheet: CssStyleSheet by lazy {
        CssStyleSheet(
            rules = emptyList(),
            keyframes = keyframes,
            isPrecompiled = true,
            sourceCssText = ""
        ).also { sheet ->
            BlinkStyleEngine.registerRuleSet(sheet, ruleSet)
        }
    }

    open fun attachTo(application: IApplication) {
        BlinkStyleEngine.registerStyleSheet(this)
        application.addStyleSheet(this)
    }

    companion object {
        fun fromPrecompiled(precompiled: PrecompiledStyleSheet): BlinkStyleSheet {
            if (precompiled is BlinkStyleSheet) return precompiled
            val bucketMap = BlinkRuleBucketMap.fromCompiledStyleRules(precompiled.compiledRules)
            return object : BlinkStyleSheet() {
                override val name: String = precompiled.name
                override val cssResourcePath: String = precompiled.cssResourcePath
                override val ruleSet: BlinkRuleBucketMap = bucketMap
            }
        }
    }
}
