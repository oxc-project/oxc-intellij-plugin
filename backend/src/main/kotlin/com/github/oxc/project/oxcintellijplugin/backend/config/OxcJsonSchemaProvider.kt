package com.github.oxc.project.oxcintellijplugin.backend.config

import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolDefinition
import com.github.oxc.project.oxcintellijplugin.backend.tool.ToolPackage
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.jetbrains.jsonSchema.extension.ContentAwareJsonSchemaFileProvider
import com.jetbrains.jsonSchema.remote.JsonSchemaCatalogExclusion

/**
 * The schema in the installed package of the tool that reads the config, so that it matches the tool's
 * version and needs no network. Two files with the same name can need different schemas, which a
 * `JsonSchemaProviderFactory` can't express, so this is asked for each file instead.
 */
class OxcJsonSchemaProvider : ContentAwareJsonSchemaFileProvider {

  override fun getSchemaFile(psiFile: PsiFile): VirtualFile? {
    val project = psiFile.project
    val file = psiFile.originalFile.virtualFile ?: return null
    // Looking for packages can need indexes.
    if (project.isDefault || DumbService.isDumb(project)) return null

    return CachedValuesManager.getCachedValue(psiFile) {
      val definition = ToolDefinition.all.firstOrNull { it.isJsonConfigFile(project, file) }
      CachedValueProvider.Result.create(
        definition?.let { ToolPackage(project, it).findSchema(file) },
        VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS,
        ProjectRootModificationTracker.getInstance(project),
        *ToolDefinition.all.map { it.settings(project).modificationTracker }.toTypedArray(),
      )
    }
  }
}

/**
 * The schema catalog, which is on by default, maps both tools' config names to the schema on Oxc's `main`
 * branch, and the platform asks it before [OxcJsonSchemaProvider]. That schema can be ahead of or behind
 * the installed tool, so these files get the installed tool's schema, or none.
 */
class OxcSchemaCatalogExclusion : JsonSchemaCatalogExclusion {

  override fun isExcluded(file: VirtualFile): Boolean = ToolDefinition.all.any { file.name in it.jsonConfigNames }
}
