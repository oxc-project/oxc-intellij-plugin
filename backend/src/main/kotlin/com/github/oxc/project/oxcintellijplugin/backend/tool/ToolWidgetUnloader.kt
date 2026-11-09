package com.github.oxc.project.oxcintellijplugin.backend.tool

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.status.EditorBasedStatusBarPopup
import com.intellij.openapi.wm.impl.status.TextPanel

/**
 * Refreshes the language services status bar widget before the plugin unloads, so that it doesn't keep the
 * plugin's classes alive.
 *
 * The widget draws [ToolWidgetItem]'s Oxc icon, and keeps that icon in its panel. Once the plugin's items are
 * gone, the widget is usually hidden, and hiding it leaves the old icon in place, which holds Oxc.Shared's
 * class loader through the icon's resource loader. The widget's own update runs later, after the unload, so
 * this clears the icon now and asks the widget to rebuild from the services that are left.
 */
internal class ToolWidgetUnloader : DynamicPluginListener {

  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    if (pluginDescriptor.pluginId.idString != PLUGIN_ID) return
    for (project in ProjectManager.getInstance().openProjects) {
      val widget = WindowManager.getInstance().getStatusBar(project)?.getWidget(LANGUAGE_SERVICES_WIDGET_ID)
        as? EditorBasedStatusBarPopup ?: continue
      (widget.component as? TextPanel.WithIconAndArrows)?.icon = null
      widget.update()
    }
  }

  private companion object {
    const val PLUGIN_ID = "com.github.oxc.project.oxcintellijplugin"

    /** The platform's `LANGUAGE_SERVICES_WIDGET_ID`, which is internal. */
    const val LANGUAGE_SERVICES_WIDGET_ID = "LanguageServiceStatusBarWidget"
  }
}
