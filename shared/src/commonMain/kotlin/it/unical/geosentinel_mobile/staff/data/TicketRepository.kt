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

const val TICKET_PAGE_SIZE = 50

/**
 * Ticket di manutenzione (M8.2), solo `PARK_MANAGER` e `ADMIN`. Le modifiche mandano in `If-Match` l'ETag dell'ultima
 * lettura: se è vecchio il server risponde 409 `concurrent-modification`. Nessuna si ritenta da sola.
 */
class TicketRepository(httpClient: () -> HttpClient, apiBaseUrl: String) {
    private val client by lazy(httpClient)
    private val api by lazy { MaintenanceApi(apiBaseUrl, client) }

    suspend fun search(
        status: MaintenanceApi.StatusSearchTickets?,
        priority: MaintenanceApi.PrioritySearchTickets?,
        assignedTo: String?,
    ): Result<PagedModelTicketResponse> = apiCall {
        api.searchTickets(status = status, priority = priority, assignedTo = assignedTo, page = 0, size = TICKET_PAGE_SIZE)
    }

    suspend fun get(id: String): Result<Versioned<TicketResponse>> = apiCallVersioned { api.getTicket(id) }

    suspend fun create(request: CreateTicketRequest): Result<TicketResponse> = apiCall { api.createTicket(request) }

    suspend fun update(id: String, etag: String, request: UpdateTicketRequest): Result<Versioned<TicketResponse>> =
        apiCallVersioned { api.updateTicket(id = id, ifMatch = etag, updateTicketRequest = request) }

    suspend fun assign(id: String, etag: String, operatorId: String): Result<Versioned<TicketResponse>> =
        apiCallVersioned { api.assignTicket(id = id, ifMatch = etag, assignTicketRequest = AssignTicketRequest(operatorId = operatorId)) }

    suspend fun changeStatus(id: String, etag: String, status: UpdateTicketStatusRequest.Status): Result<Versioned<TicketResponse>> =
        apiCallVersioned {
            api.changeTicketStatus(id = id, ifMatch = etag, updateTicketStatusRequest = UpdateTicketStatusRequest(status = status))
        }
}
