package net.papirus.pyrusservicedesk.sdk.response

import net.papirus.pyrusservicedesk.sdk.data.TicketShortDescription

/**
 * Response on [GetTicketsRequest]
 */
internal class GetTicketsResponse(
    error: ResponseError? = null,
    tickets: List<TicketShortDescription>? = null)
    : ResponseBase<List<TicketShortDescription>>(error, tickets)
