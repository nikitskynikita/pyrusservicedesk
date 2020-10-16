package com.pyrus.pyrusservicedesk.sdk.updates

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import com.pyrus.pyrusservicedesk.sdk.RequestFactory
import com.pyrus.pyrusservicedesk.sdk.data.TicketShortDescription
import com.pyrus.pyrusservicedesk.sdk.response.ResponseCallback
import com.pyrus.pyrusservicedesk.sdk.response.ResponseError
import com.pyrus.pyrusservicedesk.utils.MILLISECONDS_IN_DAY
import com.pyrus.pyrusservicedesk.utils.MILLISECONDS_IN_MINUTE
import com.pyrus.pyrusservicedesk.utils.PREFERENCE_KEY_LAST_ACTIVITY_TIME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

private const val LONG_TICKETS_UPDATE_INTERVAL_MINUTES = 30L
private const val SHORT_TICKETS_UPDATE_INTERVAL_MINUTES = 3L
private const val LAST_ACTIVITY_INTERVAL_DAYS = 3L

/**
 * Class for recurring requesting data.
 * Exposes notifications of unread ticket count changes, of new reply from support received.
 * Also exposes result of requesting data.
 *
 * Subscription types: [LiveUpdateSubscriber], [NewReplySubscriber], [OnUnreadTicketCountChangedSubscriber]
 */
internal class LiveUpdates(requests: RequestFactory, private val preferences: SharedPreferences) {

    private var ticketsUpdateInterval: Long

    init {
        ticketsUpdateInterval = getTicketsUpdateInterval(preferences.getLong(PREFERENCE_KEY_LAST_ACTIVITY_TIME, -1L))
    }

    // notified in UI thread
    private val dataSubscribers = mutableSetOf<LiveUpdateSubscriber>()
    private val newReplySubscribers = mutableSetOf<NewReplySubscriber>()
    private val ticketCountChangedSubscribers = mutableSetOf<OnUnreadTicketCountChangedSubscriber>()

    private var recentUnreadCounter = 0

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isStarted = false

    private val ticketsUpdateRunnable = object : Runnable {
        override fun run() {
            Log.d("SDS", "ticketsUpdateRunnable " + System.currentTimeMillis().toString())
            GlobalScope.launch(Dispatchers.IO) {
                requests.getTicketsRequest().execute(
                    object : ResponseCallback<List<TicketShortDescription>> {
                        override fun onSuccess(data: List<TicketShortDescription>) {
                            val newUnread = data.count { !it.isRead }
                            this@launch.launch(Dispatchers.Main) {
                                if (data.isEmpty())
                                    stopUpdates()
                                else
                                    processSuccess(data, newUnread)
                            }
                        }

                        override fun onFailure(responseError: ResponseError) {
                            // ¯\_(ツ)_/¯
                        }
                    }
                )
            }
            mainHandler.postDelayed(this, ticketsUpdateInterval * MILLISECONDS_IN_MINUTE)
        }
    }

    /**
     * Registers [subscriber] to on new reply events
     */
    @MainThread
    fun subscribeOnReply(subscriber: NewReplySubscriber) {
        newReplySubscribers.add(subscriber)
        if (recentUnreadCounter > 0)
            subscriber.onNewReply()
        onSubscribe()
    }

    /**
     * Unregisters [subscriber] from new reply events
     */
    @MainThread
    fun unsubscribeFromReplies(subscriber: NewReplySubscriber) {
        newReplySubscribers.remove(subscriber)
        onUnsubscribe()
    }

    /**
     * Registers [subscriber] on unread ticket count changed events
     */
    @MainThread
    internal fun subscribeOnUnreadTicketCountChanged(subscriber: OnUnreadTicketCountChangedSubscriber) {
        ticketCountChangedSubscribers.add(subscriber)
        onSubscribe()
    }

    /**
     * Unregisters [subscriber] from unread ticket count changed events
     */
    @MainThread
    internal fun unsubscribeFromTicketCountChanged(subscriber: OnUnreadTicketCountChangedSubscriber) {
        ticketCountChangedSubscribers.remove(subscriber)
        onUnsubscribe()
    }

    /**
     * Registers [liveUpdateSubscriber] on new data received event
     */
    @MainThread
    internal fun subscribeOnData(liveUpdateSubscriber: LiveUpdateSubscriber) {
        dataSubscribers.add(liveUpdateSubscriber)
        onSubscribe()
    }

    /**
     * Unregisters [liveUpdateSubscriber] from new data received event
     */
    @MainThread
    internal fun unsubscribeFromData(liveUpdateSubscriber: LiveUpdateSubscriber) {
        dataSubscribers.remove(liveUpdateSubscriber)
        onUnsubscribe()
    }

    /**
     * Start tickets update if it is not already running.
     */
    internal fun startUpdatesIfNeeded(lastActiveTime: Long) {
        val currentLastActiveTime = preferences.getLong(PREFERENCE_KEY_LAST_ACTIVITY_TIME, -1L)
        if (lastActiveTime > currentLastActiveTime)
            preferences.edit().putLong(PREFERENCE_KEY_LAST_ACTIVITY_TIME, lastActiveTime).apply()
        val interval = getTicketsUpdateInterval(lastActiveTime)
        if (interval == ticketsUpdateInterval && isStarted)
            return
        ticketsUpdateInterval = interval
        if (isStarted)
            stopUpdates()
        if (ticketsUpdateInterval != -1L)
            startUpdates()
    }

    private fun onSubscribe() {
        startUpdatesIfNeeded(preferences.getLong(PREFERENCE_KEY_LAST_ACTIVITY_TIME, -1L))
    }

    private fun onUnsubscribe() {
        if (!isStarted)
            return

        if (newReplySubscribers.isEmpty()
                && ticketCountChangedSubscribers.isEmpty()
                && dataSubscribers.isEmpty())
            stopUpdates()
    }

    private fun getTicketsUpdateInterval(lastActiveTime: Long): Long {
        return when {
            lastActiveTime == -1L -> -1L
            System.currentTimeMillis() - lastActiveTime < LAST_ACTIVITY_INTERVAL_DAYS * MILLISECONDS_IN_DAY ->
                SHORT_TICKETS_UPDATE_INTERVAL_MINUTES
            else -> LONG_TICKETS_UPDATE_INTERVAL_MINUTES
        }
    }

    private fun startUpdates() {
        isStarted = true
        mainHandler.post(ticketsUpdateRunnable)
    }

    private fun stopUpdates() {
        isStarted = false
        mainHandler.removeCallbacks(ticketsUpdateRunnable)
    }

    @MainThread
    private fun processSuccess(data: List<TicketShortDescription>, newUnreadCount: Int) {
        val isChanged = recentUnreadCounter != newUnreadCount
        dataSubscribers.forEach {
            it.onNewData(data)
            if (isChanged)
                it.onUnreadTicketCountChanged(newUnreadCount)
        }
        if (isChanged) {
            ticketCountChangedSubscribers.forEach { it.onUnreadTicketCountChanged(newUnreadCount) }
            if (newUnreadCount > 0)
                newReplySubscribers.forEach { it.onNewReply() }
        }
        recentUnreadCounter = newUnreadCount
    }

}

/**
 * Interface for observing updates of data.
 */
internal interface LiveUpdateSubscriber: OnUnreadTicketCountChangedSubscriber {
    /**
     * Invoked when new portion of [tickets] data is received.
     */
    fun onNewData(tickets: List<TicketShortDescription>)
}

/**
 * Interface for observing changes of unread tickets count.
 */
internal interface OnUnreadTicketCountChangedSubscriber{
    /**
     * Invoked when count of unread tickets is changed.
     */
    fun onUnreadTicketCountChanged(unreadTicketCount: Int)
}