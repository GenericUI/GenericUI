package com.nexustools.genericui.plugin

import java.io.Serializable

/**
 * Specification for an Android AppWidget `<receiver>` (`APPWIDGET_UPDATE`) and its generated
 * `res/xml/<widget>_info.xml` (`<appwidget-provider>`) metadata.
 */
open class GenericUiAndroidWidgetSpec(val providerClass: String) : Serializable {
    var minWidthDp: Int = 180
    var minHeightDp: Int = 110
    var updatePeriodMillis: Int = 86400000
    var previewImage: String? = null
    var initialLayout: String? = null
    var resizeMode: String = "horizontal|vertical"
    var widgetCategory: String = "home_screen"
    var descriptionRes: String? = null

    /** Returns an independent deep copy of this widget specification. */
    fun copySpec(): GenericUiAndroidWidgetSpec = GenericUiAndroidWidgetSpec(providerClass).also { copy ->
        copy.minWidthDp = minWidthDp
        copy.minHeightDp = minHeightDp
        copy.updatePeriodMillis = updatePeriodMillis
        copy.previewImage = previewImage
        copy.initialLayout = initialLayout
        copy.resizeMode = resizeMode
        copy.widgetCategory = widgetCategory
        copy.descriptionRes = descriptionRes
    }
}
