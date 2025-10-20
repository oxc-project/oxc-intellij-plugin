package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.extensions.isOxfmtConfigFile
import com.github.oxc.project.oxcintellijplugin.settings.OxfmtSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import org.jetbrains.annotations.Nls

class OxfmtSchemaProviderFactory : JsonSchemaProviderFactory {

    override fun getProviders(project: Project): List<JsonSchemaFileProvider?> {
        return listOf(object : JsonSchemaFileProvider {
            override fun isAvailable(file: VirtualFile): Boolean {
                val settings = OxfmtSettings.getInstance(project)
                return settings.configPath == file.path || file.isOxfmtConfigFile()
            }

            override fun getName(): @Nls String {
                return OxcBundle.message("oxlint.schema.name")
            }

            override fun getSchemaFile(): VirtualFile? {
                return JsonFileResolver.urlToFile(
                    "https://raw.githubusercontent.com/oxc-project/oxc/main/npm/oxfmt/configuration_schema.json")
            }

            override fun getSchemaType(): SchemaType {
                return SchemaType.remoteSchema
            }
        })
    }
}
