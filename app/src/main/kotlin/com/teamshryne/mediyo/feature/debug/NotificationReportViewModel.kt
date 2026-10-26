package com.teamshryne.mediyo.feature.debug

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Drives the report screen: one run at a time, results streamed in as each
 * check finishes so the screen can animate them in instead of jumping from
 * "nothing" to "everything".
 */
@HiltViewModel
class NotificationReportViewModel @Inject constructor(
    private val runner: NotificationReportRunner,
) : ViewModel() {

    data class UiState(
        val running: Boolean = false,
        val runningTitle: String? = null,
        val checks: List<CheckResult> = emptyList(),
        val report: File? = null,
        val failure: String? = null,
        val total: Int = NotificationReportRunner.CHECK_COUNT,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var job: Job? = null

    fun run() {
        // Tapping "run" twice must not interleave two reports into one list.
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            _state.value = UiState(running = true)
            try {
                val file = runner.run(
                    onStart = { title -> _state.update { it.copy(runningTitle = title) } },
                    onResult = { result -> _state.update { it.copy(checks = it.checks + result) } },
                )
                _state.update { it.copy(running = false, runningTitle = null, report = file) }
            } catch (e: Throwable) {
                // The report is a debugging aid: a failure to produce it must
                // never look like a crash of the thing being tested.
                _state.update {
                    it.copy(
                        running = false,
                        runningTitle = null,
                        failure = e.message ?: e.javaClass.simpleName,
                    )
                }
            }
        }
    }
}
