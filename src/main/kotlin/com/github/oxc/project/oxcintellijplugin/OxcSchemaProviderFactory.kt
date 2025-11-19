package com.github.oxc.project.oxcintellijplugin

import com.github.oxc.project.oxcintellijplugin.extensions.isOxcConfigFile
import com.github.oxc.project.oxcintellijplugin.settings.OxcSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import org.jetbrains.annotations.Nls

class OxcSchemaProviderFactory : JsonSchemaProviderFactory {

    override fun getProviders(project: Project): List<JsonSchemaFileProvider?> {
        return listOf(object : JsonSchemaFileProvider {
            override fun isAvailable(file: VirtualFile): Boolean {
                val settings = OxcSettings.getInstance(project)
                return settings.state.configPath == file.path || file.isOxcConfigFile()
            }

            override fun getName(): @Nls String {
                return OxlintBundle.message("oxc.schema.name")
            }

            override fun getSchemaFile(): VirtualFile? {
                return JsonFileResolver.urlToFile(
                    "https://raw.githubusercontent.com/oxc-project/oxc/main/npm/oxlint/configuration_schema.json")
            }

            override fun getSchemaType(): SchemaType {
                return SchemaType.remoteSchema
            }
        })
    }
}
