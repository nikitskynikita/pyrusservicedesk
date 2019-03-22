package net.papirus.pyrusservicedesk

import android.app.Activity
import android.app.Application
import android.arch.lifecycle.Observer
import android.content.Intent
import kotlinx.coroutines.asCoroutineDispatcher
import net.papirus.pyrusservicedesk.presentation.ui.navigation_page.ticket.TicketActivity
import net.papirus.pyrusservicedesk.presentation.ui.navigation_page.tickets.TicketsActivity
import net.papirus.pyrusservicedesk.presentation.viewmodel.LiveUpdates
import net.papirus.pyrusservicedesk.presentation.viewmodel.QuitViewModel
import net.papirus.pyrusservicedesk.sdk.FileResolver
import net.papirus.pyrusservicedesk.sdk.RepositoryFactory
import net.papirus.pyrusservicedesk.sdk.RequestFactory
import net.papirus.pyrusservicedesk.sdk.data.LocalDataProvider
import net.papirus.pyrusservicedesk.sdk.web.retrofit.RetrofitWebRepository
import net.papirus.pyrusservicedesk.utils.isTablet
import java.util.concurrent.Executors

class PyrusServiceDesk private constructor(
        internal val application: Application,
        internal val appId: String,
        internal val isSingleChat: Boolean){

    internal var userId: Int = 0

    companion object {
        internal val DISPATCHER_IO_SINGLE = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
        private var INSTANCE: PyrusServiceDesk? = null
        private var CONFIGURE: ServiceDeskConfigure? = null

        @JvmStatic
        fun init(application: Application, appId: String) {
            INSTANCE = PyrusServiceDesk(application, appId, true)
        }

        @JvmStatic
        fun start(activity: Activity, configure: ServiceDeskConfigure? = null) {
            startImpl(activity = activity, configure = configure)
        }

        @JvmStatic
        fun startTicket(ticketId: Int, activity: Activity, configure: ServiceDeskConfigure? = null) {
            startImpl(ticketId, activity, configure)
        }

        @JvmStatic
        fun subscribeOnUnreadCounterChanged(subscriber: UnreadCounterChangedSubscriber){
            getInstance().liveUpdates.subscribeOnUnreadCounterChanged(subscriber)
        }

        @JvmStatic
        fun unsubscribeFromUnreadCounterChanged(subscriber: UnreadCounterChangedSubscriber) {
            getInstance().liveUpdates.unsubscribeFromUnreadCounterChanged(subscriber)
        }

        internal fun getInstance() : PyrusServiceDesk {
            return checkNotNull(INSTANCE){ "Instantiate PyrusServiceDesk first" }
        }

        internal fun getTheme(): ServiceDeskConfigure {
            if (CONFIGURE == null)
                CONFIGURE = ServiceDeskConfigure(isDialogTheme = getInstance().application.isTablet())
            return CONFIGURE!!
        }

        private fun startImpl(ticketId: Int? = null, activity: Activity, configure: ServiceDeskConfigure? = null) {
            CONFIGURE = configure
            activity.startActivity(createIntent(ticketId))
        }

        private fun createIntent(ticketId: Int? = null): Intent {
            return when{
                ticketId != null -> TicketActivity.getLaunchIntent(ticketId)
                PyrusServiceDesk.getInstance().isSingleChat -> TicketActivity.getLaunchIntent()
                else -> TicketsActivity.getLaunchIntent()
            }
        }
    }

    internal val requestFactory: RequestFactory
    internal val liveUpdates: LiveUpdates

    internal val localDataProvider: LocalDataProvider by lazy {
        LocalDataProvider(fileResolver =  fileResolver)
    }

    private val fileResolver: FileResolver = FileResolver(application.contentResolver)

    init {
        requestFactory = RequestFactory(
            RepositoryFactory.create(
                RetrofitWebRepository(
                    appId,
                    userId.toString(),
                    fileResolver
                )
            )
        )
        liveUpdates = LiveUpdates(requestFactory)
    }

    internal fun getSharedViewModel(): QuitViewModel {
        if (quitViewModel == null)
            refreshSharedViewModel()
        return quitViewModel!!
    }


    private var quitViewModel: QuitViewModel? = null

    private val quitObserver = Observer<Boolean> {
        it?.let{value ->
            if (value)
                refreshSharedViewModel()
        }
    }

    private fun refreshSharedViewModel() {
        quitViewModel?.getQuitServiceDeskLiveData()?.removeObserver(quitObserver)
        quitViewModel = QuitViewModel().also { it.getQuitServiceDeskLiveData().observeForever(quitObserver) }
    }
}