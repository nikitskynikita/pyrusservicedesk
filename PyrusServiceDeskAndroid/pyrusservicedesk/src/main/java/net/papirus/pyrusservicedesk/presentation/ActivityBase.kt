package net.papirus.pyrusservicedesk.presentation

import android.arch.lifecycle.Observer
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.view.ViewCompat
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.example.pyrusservicedesk.R
import kotlinx.android.synthetic.main.psd_activity_ticket.*
import kotlinx.coroutines.*
import net.papirus.pyrusservicedesk.PyrusServiceDesk
import net.papirus.pyrusservicedesk.presentation.viewmodel.QuitViewModel
import net.papirus.pyrusservicedesk.utils.getViewModel
import kotlin.coroutines.CoroutineContext


/**
 * Base class for service desk activities.
 */
internal abstract class ActivityBase: AppCompatActivity(), CoroutineScope {

    private companion object {
        const val SHOW_KEYBOARD_RETRY_DELAY_MS = 100L
    }

    /**
     * Implementations should provide layout resource ids to be inflated to content view
     */
    abstract val layoutResId: Int

    /**
     * Implementations should provide id of toolbar view to be used as action bar.
     */
    abstract val toolbarViewId: Int

    /**
     * All activities share the same model to be able to trigger "rage" quit event that will close
     * all service desk activities of the current task.
     */
    protected val quitViewModel: QuitViewModel by getViewModel(QuitViewModel::class.java)

    private var recentContentHeight = 0

    override val coroutineContext: CoroutineContext = Dispatchers.Main + Job()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition()
        val theme = when{
            PyrusServiceDesk.getConfiguration().isDialogTheme -> R.style.PyrusServiceDesk_Dialog
            else -> R.style.PyrusServiceDesk
        }
        setTheme(theme)
        setContentView(layoutResId)
        setSupportActionBar(findViewById(toolbarViewId))
        findViewById<View>(android.R.id.content).apply {
            viewTreeObserver.addOnGlobalLayoutListener {
                val changedHeight = recentContentHeight - height
                if (changedHeight != 0)
                    onViewHeightChanged(changedHeight)
                recentContentHeight = height
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineContext.cancel()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        startObserveData()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!isValidPermissionRequestCode(requestCode))
            return
        val granted = mutableListOf<String>()
        permissions.forEachIndexed {
                index, permission ->
            if (grantResults[index] == PackageManager.PERMISSION_GRANTED)
                granted.add(permission)
        }
        if (!granted.isEmpty())
            onPermissionsGranted(granted.toTypedArray())
    }

    /**
     * Check whether permission [requestCode] is valid.
     */
    protected open fun isValidPermissionRequestCode(requestCode: Int): Boolean = false

    /**
     * Invoked when [permissions] are granted by user
     */
    protected open fun onPermissionsGranted(permissions: Array<String>) {}

    /**
     * Captures focus on the [view] and shows the keyboard
     */
    protected fun showKeyboardOn(view: View) {
        launch {
            while(!ViewCompat.isLaidOut(view))
                delay(SHOW_KEYBOARD_RETRY_DELAY_MS)
            view.requestFocus()
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(input, 0)
        }
    }

    /**
     * Requests [permissions] using [requestCode]. [requestCode] must be non-negative
     */
    protected fun requestPermissionsCompat(permissions: Array<String>, requestCode: Int) {
        ActivityCompat.requestPermissions(this, permissions, requestCode)
    }

    /**
     * Callback that notifies that height of the keyboard has been changed.
     */
    protected open fun onViewHeightChanged(changedBy: Int) {}

    /**
     * Extenders can safely start observe view model's data here.
     */
    protected open fun startObserveData() {
        quitViewModel.getQuitServiceDeskLiveData().observe(
            this,
            Observer { quit ->
                quit?.let {
                    if(it)
                        finish()
                }
            }
        )
    }

    private fun overridePendingTransition() {
        val enter = R.anim.psd_animation_window_enter
        val exit = R.anim.psd_animation_window_exit
        super.overridePendingTransition(enter, exit)
    }
}
