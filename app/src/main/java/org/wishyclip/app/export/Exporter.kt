package org.wishyclip.app.export

import android.content.Context

enum class ExportFormat { MP4, GIF, PNG_SEQUENCE, PNG_CURRENT_FRAME }

sealed class ExportResult {
    data class Success(val outputUri: String) : ExportResult()
    data class Failure(val message: String) : ExportResult()
    object Started : ExportResult()
    object NotImplemented : ExportResult()
}

interface Exporter {
    suspend fun export(context: Context, projectId: Long, format: ExportFormat): ExportResult
}

class ServiceExporter : Exporter {
    override suspend fun export(context: Context, projectId: Long, format: ExportFormat): ExportResult {
        ExportService.start(context, projectId, format)
        return ExportResult.Started
    }
}

object NotImplementedExporter : Exporter {
    override suspend fun export(context: Context, projectId: Long, format: ExportFormat): ExportResult =
        ExportResult.NotImplemented
}
