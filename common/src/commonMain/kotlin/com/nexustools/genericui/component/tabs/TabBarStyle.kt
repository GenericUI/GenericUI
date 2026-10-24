package com.nexustools.genericui.component.tabs

/**
 * Visual presentation style of the tab bar in a [TabContainer] or [FragmentTabContainer].
 */
enum class TabBarStyle(val cssClass: String) {
    TABS("nav-tabs"),
    PILLS("nav-pills"),
    UNDERLINE("nav-underline"),
    DOCUMENT_STRIP("nav-document-tabs")
}
