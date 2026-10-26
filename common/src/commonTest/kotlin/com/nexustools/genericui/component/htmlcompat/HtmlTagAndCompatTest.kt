package com.nexustools.genericui.component.htmlcompat

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.action.ButtonGroup
import com.nexustools.genericui.component.action.DropdownButton
import com.nexustools.genericui.component.action.FloatingActionButton
import com.nexustools.genericui.component.action.IconButton
import com.nexustools.genericui.component.action.SplitDropdownButton
import com.nexustools.genericui.component.action.ToggleButton
import com.nexustools.genericui.component.action.Toolbar
import com.nexustools.genericui.component.content.Avatar
import com.nexustools.genericui.component.content.HorizontalCarouselSelector
import com.nexustools.genericui.component.content.Icon
import com.nexustools.genericui.component.content.ImageView
import com.nexustools.genericui.component.content.MasterDetailStepSelect
import com.nexustools.genericui.component.content.Table
import com.nexustools.genericui.component.content.TableCell
import com.nexustools.genericui.component.content.TableColumn
import com.nexustools.genericui.component.content.TableRow
import com.nexustools.genericui.component.content.VectorGraphicView
import com.nexustools.genericui.component.dialog.DialogWindow
import com.nexustools.genericui.component.dock.CategorizedTreeView
import com.nexustools.genericui.component.dock.ColorSwatchBox
import com.nexustools.genericui.component.dock.DockArea
import com.nexustools.genericui.component.dock.DockGroup
import com.nexustools.genericui.component.dock.DockPanel
import com.nexustools.genericui.component.dock.DockWorkspace
import com.nexustools.genericui.component.dock.PanelWindow
import com.nexustools.genericui.component.dock.ToolPalette
import com.nexustools.genericui.component.dock.ToolPaletteItem
import com.nexustools.genericui.component.feedback.Alert
import com.nexustools.genericui.component.feedback.Banner
import com.nexustools.genericui.component.feedback.ProgressBar
import com.nexustools.genericui.component.feedback.SkeletonPlaceholder
import com.nexustools.genericui.component.feedback.Spinner
import com.nexustools.genericui.component.feedback.Toast
import com.nexustools.genericui.component.feedback.ToastContainer
import com.nexustools.genericui.component.form.CheckBox
import com.nexustools.genericui.component.form.ColorPicker
import com.nexustools.genericui.component.form.ComboBox
import com.nexustools.genericui.component.form.CompoundUnitInput
import com.nexustools.genericui.component.form.DatePicker
import com.nexustools.genericui.component.form.FileDropZone
import com.nexustools.genericui.component.form.FormGroup
import com.nexustools.genericui.component.form.InputGroup
import com.nexustools.genericui.component.form.NumberField
import com.nexustools.genericui.component.form.PasswordField
import com.nexustools.genericui.component.form.RadioButton
import com.nexustools.genericui.component.form.RadioGroup
import com.nexustools.genericui.component.form.RangeSlider
import com.nexustools.genericui.component.form.SearchField
import com.nexustools.genericui.component.form.Select
import com.nexustools.genericui.component.form.Slider
import com.nexustools.genericui.component.form.Switch
import com.nexustools.genericui.component.form.TextArea
import com.nexustools.genericui.component.form.TextField
import com.nexustools.genericui.component.form.TimePicker
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Box
import com.nexustools.genericui.component.layout.Col
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.layout.Divider
import com.nexustools.genericui.component.layout.FlexBox
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.Row
import com.nexustools.genericui.component.layout.ScrollView
import com.nexustools.genericui.component.layout.Spacer
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.lazy.LazyColumn
import com.nexustools.genericui.component.lazy.LazyGrid
import com.nexustools.genericui.component.lazy.LazyList
import com.nexustools.genericui.component.lazy.LazyRow
import com.nexustools.genericui.component.lazy.TreeView
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.navigation.Breadcrumb
import com.nexustools.genericui.component.navigation.BreadcrumbItem
import com.nexustools.genericui.component.navigation.Nav
import com.nexustools.genericui.component.navigation.Navbar
import com.nexustools.genericui.component.navigation.Pagination
import com.nexustools.genericui.component.navigation.SideNav
import com.nexustools.genericui.component.navigation.StepScrollView
import com.nexustools.genericui.component.navigation.Stepper
import com.nexustools.genericui.component.overlay.Backdrop
import com.nexustools.genericui.component.overlay.BottomSheet
import com.nexustools.genericui.component.overlay.Drawer
import com.nexustools.genericui.component.overlay.Modal
import com.nexustools.genericui.component.overlay.Popover
import com.nexustools.genericui.component.overlay.Tooltip
import com.nexustools.genericui.component.shell.CheckMenuItem
import com.nexustools.genericui.component.shell.ContextMenu
import com.nexustools.genericui.component.shell.MdiContainer
import com.nexustools.genericui.component.shell.MdiWindow
import com.nexustools.genericui.component.shell.MdiWindowState
import com.nexustools.genericui.component.shell.Menu
import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.MenuItem
import com.nexustools.genericui.component.shell.MenuSeparator
import com.nexustools.genericui.component.shell.RadioMenuItem
import com.nexustools.genericui.component.shell.SplitPane
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.surface.Accordion
import com.nexustools.genericui.component.surface.AccordionItem
import com.nexustools.genericui.component.surface.Card
import com.nexustools.genericui.component.surface.CollapsiblePanel
import com.nexustools.genericui.component.surface.Jumbotron
import com.nexustools.genericui.component.surface.ListGroup
import com.nexustools.genericui.component.surface.ListGroupItem
import com.nexustools.genericui.component.surface.Well
import com.nexustools.genericui.component.system.StatusBar
import com.nexustools.genericui.component.tabs.BadgeTab
import com.nexustools.genericui.component.tabs.CloseableTab
import com.nexustools.genericui.component.tabs.EditableTab
import com.nexustools.genericui.component.tabs.FragmentTabContainer
import com.nexustools.genericui.component.tabs.PinnedTab
import com.nexustools.genericui.component.tabs.Tab
import com.nexustools.genericui.component.tabs.TabContainer
import com.nexustools.genericui.component.typography.Badge
import com.nexustools.genericui.component.typography.Blockquote
import com.nexustools.genericui.component.typography.CodeBlock
import com.nexustools.genericui.component.typography.Heading
import com.nexustools.genericui.component.typography.Label
import com.nexustools.genericui.component.typography.MarkdownView
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.style.css.CssStyleSheet
import com.nexustools.genericui.style.sass.LiveScssStyleEngine
import com.nexustools.genericui.style.sass.SassCompiler
import com.nexustools.genericui.window.TitleBar
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HtmlTagAndCompatTest {

    @Test
    fun testButtonAndSubclassesHaveButtonHtmlTag() {
        assertEquals("button", Button("Save").htmlTag)
        assertEquals("button", IconButton("bi-gear").htmlTag)
        assertEquals("button", ToggleButton("Mute").htmlTag)
        assertEquals("button", SplitDropdownButton("Run").htmlTag)
        assertEquals("button", FloatingActionButton("+").htmlTag)
    }

    @Test
    fun testStandardHtmlTagComponentsAnd42HtmlCompatClasses() {
        // Heading levels & H1..H6 subclasses
        for (lvl in 1..6) {
            assertEquals("h$lvl", Heading("Title", level = lvl).htmlTag)
        }
        val h1 = H1("H1")
        val h2 = H2("H2")
        val h3 = H3("H3")
        val h4 = H4("H4")
        val h5 = H5("H5")
        val h6 = H6("H6")
        assertTrue(h1 is Heading && h1.htmlTag == "h1" && h1.level == 1)
        assertTrue(h2 is Heading && h2.htmlTag == "h2" && h2.level == 2)
        assertTrue(h3 is Heading && h3.htmlTag == "h3" && h3.level == 3)
        assertTrue(h4 is Heading && h4.htmlTag == "h4" && h4.level == 4)
        assertTrue(h5 is Heading && h5.htmlTag == "h5" && h5.level == 5)
        assertTrue(h6 is Heading && h6.htmlTag == "h6" && h6.level == 6)

        // Semantic container htmlcompat classes
        assertEquals("div", Div().htmlTag)
        assertEquals("header", Header().htmlTag)
        assertEquals("footer", Footer().htmlTag)
        assertEquals("main", Main().htmlTag)
        assertEquals("section", Section().htmlTag)
        assertEquals("article", Article().htmlTag)
        assertEquals("aside", Aside().htmlTag)
        assertEquals("form", Form().htmlTag)
        assertEquals("fieldset", Fieldset().htmlTag)
        assertEquals("figure", Figure().htmlTag)
        assertEquals("details", Details().htmlTag)

        // Inline / block text & typography htmlcompat classes
        assertEquals("span", Text("inline", paragraph = false).htmlTag)
        assertEquals("p", Text("para", paragraph = true).htmlTag)
        assertEquals("span", Span("inline").htmlTag)
        assertEquals("p", P("para").htmlTag)
        assertEquals("label", Label("lbl").htmlTag)
        assertEquals("a", A("link", href = "https://example.com").htmlTag)
        assertEquals("strong", Strong("bold").htmlTag)
        assertEquals("b", B("bold").htmlTag)
        assertEquals("em", Em("italic").htmlTag)
        assertEquals("i", I("icon").htmlTag)
        assertEquals("small", Small("fine").htmlTag)
        assertEquals("sup", Sup("2").htmlTag)
        assertEquals("sub", Sub("0").htmlTag)
        assertEquals("figcaption", Figcaption("cap").htmlTag)
        assertEquals("summary", Summary("sum").htmlTag)
        assertEquals("pre", Pre("code").htmlTag)
        assertEquals("code", Code("val x = 1").htmlTag)
        assertEquals("span", Badge("New").htmlTag)
        assertEquals("blockquote", Blockquote("Quote").htmlTag)
        assertEquals("pre", CodeBlock("code").htmlTag)

        // Lists, tables, dividers, media, forms
        assertEquals("ul", Ul().htmlTag)
        assertEquals("ol", Ol().htmlTag)
        assertEquals("li", Li().htmlTag)
        assertEquals("thead", Thead().htmlTag)
        assertEquals("tbody", Tbody().htmlTag)
        assertEquals("tfoot", Tfoot().htmlTag)
        assertEquals("tr", Tr().htmlTag)
        assertEquals("th", Th().htmlTag)
        assertEquals("td", Td().htmlTag)
        assertEquals("hr", Hr().htmlTag)
        assertEquals("br", Br().htmlTag)
        assertEquals("hr", Divider().htmlTag)
        assertEquals("br", Spacer().htmlTag)
        assertEquals("img", ImageView().htmlTag)
        assertEquals("i", Icon("bi-check").htmlTag)
        assertEquals("svg", VectorGraphicView().htmlTag)
        assertEquals("table", Table().htmlTag)
        assertEquals("tr", TableRow("r1", listOf(TableCell("cell"))).htmlTag)
        assertEquals("td", TableCell("cell").htmlTag)
        assertEquals("th", TableColumn("id", "ID").htmlTag)

        assertEquals("input", TextField().htmlTag)
        assertEquals("input", PasswordField().htmlTag)
        assertEquals("input", NumberField().htmlTag)
        assertEquals("input", SearchField().htmlTag)
        assertEquals("input", CheckBox().htmlTag)
        assertEquals("input", RadioButton("opt1", "Option 1").htmlTag)
        assertEquals("input", Switch().htmlTag)
        assertEquals("input", RangeSlider().htmlTag)
        assertEquals("input", Slider().htmlTag)
        assertEquals("input", ColorPicker().htmlTag)
        assertEquals("input", DatePicker().htmlTag)
        assertEquals("input", TimePicker().htmlTag)
        assertEquals("textarea", TextArea().htmlTag)
        assertEquals("select", Select().htmlTag)
        assertEquals("select", ComboBox().htmlTag)
    }

    @Test
    fun testCustomGenericUiNamespacedTags() {
        assertEquals("genericui:container", Container().htmlTag)
        assertEquals("genericui:box", Box().htmlTag)
        assertEquals("genericui:row", Row().htmlTag)
        assertEquals("genericui:col", Col().htmlTag)
        assertEquals("genericui:flexbox", FlexBox().htmlTag)
        assertEquals("genericui:hstack", HStack().htmlTag)
        assertEquals("genericui:vstack", VStack().htmlTag)
        assertEquals("genericui:scrollview", ScrollView().htmlTag)
        assertEquals("genericui:stepscrollview", StepScrollView().htmlTag)
        assertEquals("genericui:menubar", MenuBar().htmlTag)
        assertEquals("genericui:menu", Menu("File").htmlTag)
        assertEquals("genericui:menuitem", MenuItem("Open").htmlTag)
        assertEquals("genericui:checkmenuitem", CheckMenuItem("Grid").htmlTag)
        assertEquals("genericui:radiomenuitem", RadioMenuItem("Dark", checked = true).htmlTag)
        assertEquals("genericui:menuseparator", MenuSeparator().htmlTag)
        assertEquals("genericui:contextmenu", ContextMenu().htmlTag)
        assertEquals("genericui:splitpane", SplitPane().htmlTag)
        assertEquals("genericui:mdicontainer", MdiContainer().htmlTag)
        assertEquals("genericui:mdiwindow", MdiWindow("w1", "Doc 1").htmlTag)
        assertEquals("genericui:window", Window("App").htmlTag)
        assertEquals("genericui:titlebar", TitleBar().htmlTag)
        assertEquals("genericui:statusbar", StatusBar().htmlTag)
        assertEquals("genericui:dockworkspace", DockWorkspace().htmlTag)
        assertEquals("genericui:dockarea", DockArea(DockPosition.LEFT).htmlTag)
        assertEquals("genericui:dockgroup", DockGroup().htmlTag)
        assertEquals("genericui:dockpanel", DockPanel("p1", "Tools").htmlTag)
        assertEquals("genericui:panelwindow", PanelWindow("Inspector").htmlTag)
        assertEquals("genericui:toolpalette", ToolPalette().htmlTag)
        assertEquals("genericui:toolpaletteitem", ToolPaletteItem("brush", "Brush", "bi-brush").htmlTag)
        assertEquals("genericui:categorizedtreeview", CategorizedTreeView().htmlTag)
        assertEquals("genericui:colorswatchbox", ColorSwatchBox().htmlTag)
        assertEquals("genericui:lazylist", LazyList().htmlTag)
        assertEquals("genericui:lazycolumn", LazyColumn().htmlTag)
        assertEquals("genericui:lazyrow", LazyRow().htmlTag)
        assertEquals("genericui:lazygrid", LazyGrid().htmlTag)
        assertEquals("genericui:treeview", TreeView<String>().htmlTag)
        assertEquals("genericui:card", Card().htmlTag)
        assertEquals("genericui:collapsiblepanel", CollapsiblePanel().htmlTag)
        assertEquals("genericui:accordion", Accordion().htmlTag)
        assertEquals("genericui:accordionitem", AccordionItem("sec1", "Sec").htmlTag)
        assertEquals("genericui:jumbotron", Jumbotron().htmlTag)
        assertEquals("genericui:well", Well().htmlTag)
        assertEquals("genericui:listgroup", ListGroup().htmlTag)
        assertEquals("genericui:listgroupitem", ListGroupItem().htmlTag)
        assertEquals("genericui:tabcontainer", TabContainer().htmlTag)
        assertEquals("genericui:fragmenttabcontainer", FragmentTabContainer().htmlTag)
        assertEquals("genericui:tab", Tab("t1", "Tab").htmlTag)
        assertEquals("genericui:closeabletab", CloseableTab("t2", "Close").htmlTag)
        assertEquals("genericui:pinnedtab", PinnedTab("t3", "Pin").htmlTag)
        assertEquals("genericui:badgetab", BadgeTab("t4", "Badge").htmlTag)
        assertEquals("genericui:editabletab", EditableTab("t5", "Edit").htmlTag)
        assertEquals("genericui:navbar", Navbar().htmlTag)
        assertEquals("genericui:nav", Nav().htmlTag)
        assertEquals("genericui:sidenav", SideNav().htmlTag)
        assertEquals("genericui:bottomnavigationbar", BottomNavigationBar().htmlTag)
        assertEquals("genericui:breadcrumb", Breadcrumb().htmlTag)
        assertEquals("genericui:breadcrumbitem", BreadcrumbItem("home", "Home").htmlTag)
        assertEquals("genericui:pagination", Pagination().htmlTag)
        assertEquals("genericui:stepper", Stepper().htmlTag)
        assertEquals("genericui:dropdownbutton", DropdownButton("More").htmlTag)
        assertEquals("genericui:buttongroup", ButtonGroup().htmlTag)
        assertEquals("genericui:toolbar", Toolbar().htmlTag)
        assertEquals("genericui:formgroup", FormGroup().htmlTag)
        assertEquals("genericui:inputgroup", InputGroup().htmlTag)
        assertEquals("genericui:radiogroup", RadioGroup().htmlTag)
        assertEquals("genericui:compoundunitinput", CompoundUnitInput().htmlTag)
        assertEquals("genericui:filedropzone", FileDropZone().htmlTag)
        assertEquals("genericui:markdownview", MarkdownView().htmlTag)
        assertEquals("genericui:avatar", Avatar().htmlTag)
        assertEquals("genericui:masterdetailstepselect", MasterDetailStepSelect().htmlTag)
        assertEquals("genericui:horizontalcarouselselector", HorizontalCarouselSelector().htmlTag)
        assertEquals("genericui:alert", Alert().htmlTag)
        assertEquals("genericui:banner", Banner().htmlTag)
        assertEquals("genericui:toast", Toast().htmlTag)
        assertEquals("genericui:toastcontainer", ToastContainer().htmlTag)
        assertEquals("genericui:progressbar", ProgressBar().htmlTag)
        assertEquals("genericui:spinner", Spinner().htmlTag)
        assertEquals("genericui:skeletonplaceholder", SkeletonPlaceholder().htmlTag)
        assertEquals("genericui:modal", Modal().htmlTag)
        assertEquals("genericui:drawer", Drawer().htmlTag)
        assertEquals("genericui:bottomsheet", BottomSheet().htmlTag)
        assertEquals("genericui:popover", Popover().htmlTag)
        assertEquals("genericui:tooltip", Tooltip("Tip").htmlTag)
        assertEquals("genericui:backdrop", Backdrop().htmlTag)
        assertEquals("genericui:dialogwindow", DialogWindow("Dialog").htmlTag)
    }

    @Test
    fun testCssSelectorMatchingForStandardHtmlTagsAndGenericUiCustomTags() {
        val css = """
            header h1 {
                font-size: 28px;
            }
            ul > li {
                border-width: 6px;
            }
            button.btn {
                border-radius: 9px;
            }
            genericui:mdicontainer genericui:mdiwindow {
                border-radius: 10px;
            }
            genericui\:mdicontainer > .mdi-desktop-surface > genericui\:mdiwindow.active {
                border-width: 3px;
            }
        """.trimIndent()
        val sheet = CssStyleSheet.parse(css)

        val header = Header()
        val h1 = H1("App Header")
        header.addChild(h1)

        val ul = Ul()
        val li = Li()
        ul.addChild(li)

        val btn = Button("Click")

        val mdi = MdiContainer()
        val win1 = mdi.createWindow("w1", "Window 1")

        val h1Style = sheet.resolveStyleForComponent(h1)
        assertEquals(28f, h1Style.fontSizePx)

        val liStyle = sheet.resolveStyleForComponent(li)
        assertEquals(6f, liStyle.borderWidthPx)

        val btnStyle = sheet.resolveStyleForComponent(btn)
        assertEquals(9f, btnStyle.borderRadiusPx)

        val winStyle = sheet.resolveStyleForComponent(win1)
        assertEquals(10f, winStyle.borderRadiusPx)
        assertEquals(3f, winStyle.borderWidthPx)
    }

    @Test
    fun testMdiContainerAndMdiWindowOperations() {
        val mdi = MdiContainer(showTaskbar = true)
        val w1 = mdi.createWindow("w1", "Scene 1", bounds = Rect(10, 10, 300, 200))
        val frag = object : Fragment() {}
        val w2 = mdi.createFragmentWindow("w2", "Inspector", frag, bounds = Rect(40, 40, 320, 220))

        assertEquals(2, mdi.windows.size)
        assertEquals(w2, mdi.activeWindow)
        assertTrue(w2.isActiveWindow)

        mdi.activateWindow(w1)
        assertEquals(w1, mdi.activeWindow)
        assertTrue(w1.isActiveWindow)

        mdi.minimizeWindow(w1)
        assertEquals(MdiWindowState.MINIMIZED, w1.windowState)
        assertTrue(!w1.isVisible)

        mdi.restoreWindow(w1)
        assertEquals(MdiWindowState.NORMAL, w1.windowState)
        assertTrue(w1.isVisible)

        mdi.maximizeWindow(w1)
        assertEquals(MdiWindowState.MAXIMIZED, w1.windowState)

        mdi.cascadeWindows(startX = 20, startY = 20, stepX = 30, stepY = 30)
        assertEquals(MdiWindowState.NORMAL, w1.windowState)
        assertEquals(20, w1.windowBounds.x)
        assertEquals(50, w2.windowBounds.x)

        mdi.tileWindows(columns = 2, viewportWidth = 800, viewportHeight = 600, gapPx = 0)
        assertEquals(400, w1.windowBounds.width)
        assertEquals(400, w2.windowBounds.width)

        mdi.removeWindow(w2)
        assertEquals(1, mdi.windows.size)
        assertNull(mdi.findWindow("w2"))
    }

    @Test
    fun testLiveScssStyleEngineAndSassCompiler() {
        val root = Div()
        val mdi = MdiContainer()
        val win = mdi.createWindow("editor", "Live SCSS")
        root.addChild(mdi)

        val engine = LiveScssStyleEngine(
            initialScss = """
                ${'$'}win-radius: 12px;
                @mixin elevated(${'$'}bw: 2px) {
                    border-width: ${'$'}bw;
                }
                genericui:mdicontainer {
                    genericui:mdiwindow {
                        border-radius: ${'$'}win-radius;
                        @include elevated();
                    }
                }
            """.trimIndent()
        )
        engine.bindRootComponent(root)

        assertTrue(engine.lastCompileResult.isSuccess)
        assertEquals(12f, win.computedStyle?.borderRadiusPx)
        assertEquals(2f, win.computedStyle?.borderWidthPx)

        // Mutate variable live at runtime
        val varResult = engine.setVariable("win-radius", "18px")
        assertTrue(varResult.isSuccess)
        assertEquals(18f, win.computedStyle?.borderRadiusPx)

        // Verify syntax error detection keeps previous valid stylesheet
        val badResult = engine.updateScss("genericui:mdiwindow { border-radius: 4px; ")
        assertTrue(!badResult.isSuccess)
        assertTrue(engine.errors.isNotEmpty())
        assertEquals(18f, win.computedStyle?.borderRadiusPx)
        assertTrue(SassCompiler.validateScss("div { color: #fff; }").isEmpty())
    }
}
