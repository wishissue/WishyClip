package org.wishyclip.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the full-screen loading overlay shows. [progress] null means "indeterminate". */
data class BusyState(
    val owner: String,
    val title: String,
    val detail: String = "",
    val progress: Float? = null,
    val onCancel: (() -> Unit)? = null
)

/**
 * Process-wide "something long is running" state. The export service lives in the same process as
 * the UI, so it can publish progress here and the editor overlay just observes [state].
 * Each task passes its own [owner] so one task can never clear another's overlay.
 */
object BusyTracker {
    private val _state = MutableStateFlow<BusyState?>(null)
    val state: StateFlow<BusyState?> = _state.asStateFlow()

    fun show(owner: String, title: String, detail: String = "", progress: Float? = null, onCancel: (() -> Unit)? = null) {
        _state.value = BusyState(owner, title, detail, progress?.coerceIn(0f, 1f), onCancel)
    }

    /** Updates the overlay only while [owner] still owns it. */
    fun update(owner: String, detail: String? = null, progress: Float? = null) {
        _state.update { cur ->
            if (cur == null || cur.owner != owner) cur
            else cur.copy(
                detail = detail ?: cur.detail,
                progress = progress?.coerceIn(0f, 1f) ?: cur.progress
            )
        }
    }

    private val _notice = MutableStateFlow<String?>(null)

    /** One-shot result text ("Export complete", "Export failed: ...") the UI shows once and clears. */
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun postNotice(text: String) { _notice.value = text }

    fun consumeNotice() { _notice.value = null }

    fun hide(owner: String) {
        _state.update { cur -> if (cur?.owner == owner) null else cur }
    }
}
