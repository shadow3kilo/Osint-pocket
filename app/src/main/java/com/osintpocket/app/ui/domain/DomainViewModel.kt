package com.osintpocket.app.ui.domain

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osintpocket.app.core.network.safeCall
import com.osintpocket.app.core.network.toUserMessage
import com.osintpocket.app.core.ui.KeyValue
import com.osintpocket.app.core.ui.SectionState
import com.osintpocket.app.data.DnsRecords
import com.osintpocket.app.data.DomainRepository
import com.osintpocket.app.data.history.HistoryModule
import com.osintpocket.app.data.history.HistoryRepository
import com.osintpocket.app.ui.common.asPlainText
import com.osintpocket.app.data.remote.InternetDbHost
import com.osintpocket.app.data.remote.IpGeo
import com.osintpocket.app.domain.Target
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DomainUiState(
    val query: String = "",
    val inputError: String? = null,
    val target: Target? = null,
    val resolvedIp: String? = null,
    val rdap: SectionState<List<KeyValue>> = SectionState.Idle,
    val dns: SectionState<DnsRecords> = SectionState.Idle,
    val subdomains: SectionState<List<String>> = SectionState.Idle,
    val exposure: SectionState<InternetDbHost?> = SectionState.Idle,
    val geo: SectionState<IpGeo> = SectionState.Idle,
) {
    val isBusy: Boolean
        get() = listOf(rdap, dns, subdomains, exposure, geo).any { it is SectionState.Loading }
}

@HiltViewModel
class DomainViewModel @Inject constructor(
    private val repo: DomainRepository,
    private val history: HistoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DomainUiState())
    val state: StateFlow<DomainUiState> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(q: String) = _state.update { it.copy(query = q, inputError = null) }

    fun cancel() {
        job?.cancel()
        _state.update { s ->
            fun <T> SectionState<T>.stop() = if (this is SectionState.Loading) SectionState.Error("Cancelado") else this
            s.copy(rdap = s.rdap.stop(), dns = s.dns.stop(), subdomains = s.subdomains.stop(),
                exposure = s.exposure.stop(), geo = s.geo.stop())
        }
    }

    fun search() {
        val target = Target.parse(_state.value.query)
        if (target == null) {
            _state.update { it.copy(inputError = "Introduce un dominio o una IP válidos") }
            return
        }
        job?.cancel()

        val loading = SectionState.Loading
        _state.update {
            when (target) {
                is Target.Domain -> DomainUiState(
                    query = it.query, target = target,
                    rdap = loading, dns = loading, subdomains = loading, exposure = loading, geo = loading,
                )
                is Target.Ip -> DomainUiState(
                    query = it.query, target = target, resolvedIp = target.value,
                    rdap = loading, exposure = loading, geo = loading,
                )
            }
        }

        job = viewModelScope.launch {
            when (target) {
                is Target.Domain -> searchDomain(target.value)
                is Target.Ip -> searchIp(target.value)
            }
            recordHistory(_state.value)
        }
    }

    private suspend fun searchDomain(domain: String) = coroutineScope {
        launch { load({ repo.rdapDomain(domain) }) { copy(rdap = it) } }
        launch { load({ repo.subdomains(domain) }) { copy(subdomains = it) } }
        launch {
            val ip = load({ repo.dns(domain) }) { copy(dns = it) }?.firstIp()
            if (ip == null) {
                val msg = SectionState.Error("Sin registros A/AAAA para consultar")
                _state.update { it.copy(exposure = msg, geo = msg) }
            } else {
                _state.update { it.copy(resolvedIp = ip) }
                ipIntel(ip)
            }
        }
    }

    private suspend fun searchIp(ip: String) = coroutineScope {
        launch { load({ repo.rdapIp(ip) }) { copy(rdap = it) } }
        ipIntel(ip)
    }

    private suspend fun ipIntel(ip: String) = coroutineScope {
        launch { load({ repo.exposure(ip) }) { copy(exposure = it) } }
        launch { load({ repo.geo(ip) }) { copy(geo = it) } }
    }

    private suspend fun recordHistory(s: DomainUiState) {
        val target = s.target ?: return
        fun <T> SectionState<T>.text(render: (T) -> String): String? = when (this) {
            is SectionState.Success -> render(data)
            is SectionState.Error -> "Error: $message"
            else -> null
        }
        val summary = buildList {
            (s.subdomains as? SectionState.Success)?.let { add("${it.data.size} subdominios") }
            (s.exposure as? SectionState.Success)?.let { add("${it.data?.ports?.size ?: 0} puertos expuestos") }
            (s.geo as? SectionState.Success)?.data?.country?.let { add(it) }
        }.ifEmpty { listOf("Consulta completada") }.joinToString(" · ")
        val details = listOfNotNull(
            s.rdap.text { it.asPlainText() }?.let { "[RDAP]\n$it" },
            s.dns.text(::dnsText)?.let { "[DNS]\n$it" },
            s.subdomains.text { it.take(500).joinToString("\n") }?.let { "[Subdominios]\n$it" },
            s.exposure.text { it?.let(::exposureText) ?: "Sin datos" }?.let { "[Exposición ${s.resolvedIp.orEmpty()}]\n$it" },
            s.geo.text { geoRows(it).asPlainText() }?.let { "[Geolocalización]\n$it" },
        ).joinToString("\n\n")
        history.record(HistoryModule.DOMAIN, target.value, summary, details)
    }

    /** Ejecuta una llamada y vuelca su resultado en la sección correspondiente. */
    private suspend fun <T> load(
        call: suspend () -> T,
        reduce: DomainUiState.(SectionState<T>) -> DomainUiState,
    ): T? {
        val result = safeCall { call() }
        val section: SectionState<T> = result.fold(
            onSuccess = { SectionState.Success(it) },
            onFailure = { SectionState.Error(it.toUserMessage()) },
        )
        _state.update { it.reduce(section) }
        return result.getOrNull()
    }
}
