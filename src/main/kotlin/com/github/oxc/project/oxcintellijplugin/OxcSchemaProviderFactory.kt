package com.github.oxc.project.oxcintellijplugin

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
                // TODO: Detect user specified config file path.
                return Constants.OXLINTRC_CONFIG_FILE == file.name
            }

            override fun getName(): @Nls String {
                return MyBundle.message("oxc.schema.name")
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
