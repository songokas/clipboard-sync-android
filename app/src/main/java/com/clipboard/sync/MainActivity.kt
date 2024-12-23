package com.clipboard.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.add
import androidx.fragment.app.commit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val mainScope = MainScope()
    private var timerHandler: Handler = Handler(Looper.getMainLooper())
    private val viewModel: SyncViewModel by viewModels()

    var userStarted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<MainFragment>(R.id.main_fragment)
            }
        }

        handleIntent()
    }

    override fun onStop() {
        super.onStop()
        Log.d(
            "stop activity",
            viewModel.isRunning().toString() + " " + viewModel.isServiceRunning()
                .toString() + " " + userStarted
        )
        if (userStarted && !viewModel.isServiceRunning()) {
            viewModel.stopSync()
        }
    }

    override fun onRestart() {
        super.onRestart()
        Log.d("restart activity", viewModel.isRunning().toString() + " " + userStarted.toString())
        if (userStarted && !viewModel.isRunning()) {
            if (viewModel.changeState(this, true)) {
                processUpdates(this)
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        userStarted = savedInstanceState.getBoolean("userStarted") == true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("userStarted", userStarted)
        super.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == R.id.action_settings) {
            startActivity(
                Intent(this, SettingsActivity::class.java)
            )
            return true
        }
        return super.onOptionsItemSelected(item)
    }

//    override fun onActivityResult(
//        requestCode: Int, resultCode: Int, resultData: Intent?
//    ) {
//        super.onActivityResult(requestCode, resultCode, resultData)
//        Log.d("activity", "received $requestCode $resultCode")
//        if (requestCode == Config.MOVE_FILES_INTENT) {
//            //@TODO remove only moved files
//            for (uri in viewModel.getAllFiles(this, filesDir)) {
//                Log.d("activity", "remove $uri")
//                try {
//                    contentResolver.delete(uri, null, null)
//                } catch (e: IllegalArgumentException) {
//                    Log.e("activity", "failed to remove file ${e.message}")
//                }
//            }
//            viewModel.updateFileCount(0)
//        }
//    }

    fun processUpdates(context: Context) {
        val runnable = object : Runnable {
            override fun run() {
                if (userStarted) {
                    if (viewModel.processStatus(context)) {
                        timerHandler.postDelayed(this, 3000)
                    } else {
                        viewModel.changeState(context, start = false)
                    }
                }
            }
        }
        timerHandler.postDelayed(runnable, 3000)
    }

    private fun handleIntent() {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val ac = this
                mainScope.launch {
                    try {
                        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let {
                            viewModel.handleSendFileAsync(ac, ac.contentResolver, it)
                                .await()
                        }
                    } catch (e: NullPointerException) {
                        viewModel.updateText("Failed to send file")
                    }
                    closeAfterIntent()
                }
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val ac = this
                mainScope.launch {
                    intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
                        ?.let { arrayList ->
                            viewModel.handleSendFilesAsync(
                                ac,
                                ac.contentResolver,
                                arrayList.filterIsInstance<Uri>()
                            )
                                .await()
                        }
                    closeAfterIntent()
                }
            }
        }
    }

    private fun closeAfterIntent() {
        val runnable = Runnable { finish() }
        val timerHandler = Handler(Looper.getMainLooper())
        timerHandler.postDelayed(runnable, 4000)
        setResult(Activity.RESULT_OK)
    }
}