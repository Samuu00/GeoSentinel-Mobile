// Accesso alle API dei ticket di manutenzione: ricerca, lettura, creazione e modifiche con il controllo di
// concorrenza ottimistico basato sull'ETag.
package it.unical.geosentinel_mobile.staff.data

import io.ktor.client.HttpClient
import it.unical.geosentinel_mobile.api.MaintenanceApi
import it.unical.geosentinel_mobile.api.model.AssignTicketRequest
import it.unical.geosentinel_mobile.api.model.CreateTicketRequest
import it.unical.geosentinel_mobile.api.model.PagedModelTicketResponse
import it.unical.geosentinel_mobile.api.model.TicketResponse
import it.unical.geosentinel_mobile.api.model.UpdateTicketRequest
import it.unical.geosentinel_mobile.api.model.UpdateTicketStatusRequest
import it.unical.geosentinel_mobile.core.network.Versioned
import it.unical.geosentinel_mobile.core.network.apiCall
import it.unical.geosentinel_mobile.core.network.apiCallVersioned

/** Numero di ticket chiesti in una ricerca (si legge solo la prima pagina). */
const val TICKET_PAGE_SIZE = 50

/**
 * Repository dei ticket di manutenzione, riservati a `PARK_MANAGER` e `ADMIN`. Ogni modifica invia nell'intestazione
 * `If-Match` l'ETag dell'ultima lettura: se nel frattempo il ticket è cambiato, il server risponde 409
 * `concurrent-modification`. Nessuna richiesta si ritenta in automatico. Le letture e le modifiche restituiscono il
 * ticket insieme al suo nuovo ETag ([Versioned]).
 */
class TicketRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {
    private val client by lazy(httpClient)
    private val api by lazy { MaintenanceApi(apiBaseUrl, client) }

    /** Cerca i ticket con i filtri facoltativi di stato, priorità e operatore assegnato. */
    suspend fun search(
        status: MaintenanceApi.StatusSearchTickets?,
        priority: MaintenanceApi.PrioritySearchTickets?,
        assignedTo: String?,
    ): Result<PagedModelTicketResponse> = apiCall {
        api.searchTickets(status = status, priority = priority, assignedTo = assignedTo, page = 0, size = TICKET_PAGE_SIZE)
    }

    /** Legge il ticket [id] con il suo ETag. */
    suspend fun get(id: String): Result<Versioned<TicketResponse>> = apiCallVersioned { api.getTicket(id) }

    /** Crea un nuovo ticket. */
    suspend fun create(request: CreateTicketRequest): Result<TicketResponse> = apiCall { api.createTicket(request) }

    /** Modifica i campi del ticket [id], a condizione che l'ETag sia ancora [etag]. */
    suspend fun update(id: String, etag: String, request: UpdateTicketRequest): Result<Versioned<TicketResponse>> =
        apiCallVersioned { api.updateTicket(id = id, ifMatch = etag, updateTicketRequest = request) }

    /** Assegna il ticket [id] all'operatore [operatorId], a condizione che l'ETag sia ancora [etag]. */
    suspend fun assign(id: String, etag: String, operatorId: String): Result<Versioned<TicketResponse>> =
        apiCallVersioned { api.assignTicket(id = id, ifMatch = etag, assignTicketRequest = AssignTicketRequest(operatorId = operatorId)) }

    /** Cambia lo stato del ticket [id], a condizione che l'ETag sia ancora [etag]. */
    suspend fun changeStatus(id: String, etag: String, status: UpdateTicketStatusRequest.Status): Result<Versioned<TicketResponse>> =
        apiCallVersioned {
            api.changeTicketStatus(id = id, ifMatch = etag, updateTicketStatusRequest = UpdateTicketStatusRequest(status = status))
        }
}
