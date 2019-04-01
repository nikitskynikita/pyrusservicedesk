package net.papirus.pyrusservicedesk.presentation.ui.navigation_page.tickets

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.MediatorLiveData
import android.arch.lifecycle.MutableLiveData
import android.arch.lifecycle.Transformations
import net.papirus.pyrusservicedesk.PyrusServiceDesk
import net.papirus.pyrusservicedesk.presentation.call_adapter.GetTicketsCallAdapter
import net.papirus.pyrusservicedesk.presentation.viewmodel.ConnectionViewModelBase
import net.papirus.pyrusservicedesk.sdk.data.TicketShortDescription
import net.papirus.pyrusservicedesk.sdk.updates.LiveUpdateSubscriber

/**
 * ViewModel for rendering UI with list of tickets
 */
internal class TicketsViewModel(serviceDesk: PyrusServiceDesk)
    : ConnectionViewModelBase(serviceDesk),
    LiveUpdateSubscriber {

    private val tickets = MediatorLiveData<List<TicketShortDescription>>()
    private val request = MutableLiveData<Boolean>()

    private var unreadCount = 0

    init{
        tickets.apply {
            addSource(
                Transformations.switchMap(request){
                    GetTicketsCallAdapter(this@TicketsViewModel, requests).execute()
                }
            ){
                onNewData(it?.data!!)
                onDataLoaded()
            }
        }
        if (isNetworkConnected.value == true) {
            loadData()
        }

        liveUpdates.subscribeOnData(this)
    }

    override fun onLoadData() {
        request.value = true
    }

    override fun onNewData(tickets: List<TicketShortDescription>) {
        this.tickets.value = tickets.sortedWith(TicketShortDescriptionComparator())
        unreadCount = tickets.count{ description -> !description.isRead }
    }

    override fun onUnreadTicketCountChanged(unreadTicketCount: Int) {
        unreadCount = unreadTicketCount
    }

    override fun onCleared() {
        super.onCleared()
        liveUpdates.unsubscribeFromData(this)
    }

    /**
     * Provides live data that delivers list of [TicketShortDescription] to be rendered.
     */
    fun getTicketsLiveData(): LiveData<List<TicketShortDescription>> = tickets

    /**
     * Provides live data that delivers count of currently unread tickets
     */
    fun getUnreadCount() = unreadCount

    /**
     * Callback to be invoked when user opens [ticket] in UI
     */
    fun onTicketOpened(ticket: TicketShortDescription) {
        if (!ticket.isRead) {
            unreadCount--
            val ticketsMutable = tickets.value!!.toMutableList()
            ticketsMutable[ticketsMutable.indexOf(ticket)] = ticket.read()
            tickets.value = ticketsMutable
        }
    }

    private class TicketShortDescriptionComparator : Comparator<TicketShortDescription> {

        override fun compare(o1: TicketShortDescription, o2: TicketShortDescription): Int {
            return when {
                o1.lastComment == null -> return when {
                    o2.lastComment == null -> o1.ticketId - o2.ticketId
                    else -> 1
                }
                o2.lastComment == null -> -1
                o1.lastComment.creationDate.before(o2.lastComment.creationDate) -> 1
                o1.lastComment.creationDate.after(o2.lastComment.creationDate) -> -1
                else -> o1.ticketId - o2.ticketId
            }
        }
    }
}

private fun TicketShortDescription.read(): TicketShortDescription {
    return TicketShortDescription(ticketId, subject, true, lastComment)
}
