package com.testing.voicedemo

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.leanback.widget.SpeechRecognitionCallback

class SearchFragment : CustomSearchSupportFragment(),
    SearchResultProvider {
  private var mQuery: String? = null
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setSearchResultProvider(this)
  }

  override fun onPause() {
    mHandler.removeCallbacksAndMessages(null)
    super.onPause()
  }

  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_SPEECH) {
      if (resultCode == Activity.RESULT_OK) {
        setSearchQuery(data!!, true)
      } else { // If recognizer is canceled or failed, keep focus on the search orb
        if (FINISH_ON_RECOGNIZER_CANCELED) {
        }
      }
    }
  }

  override fun onQueryTextSubmit(query: String?): Boolean {
    if (DEBUG) Log.i(TAG, String.format("Search text submitted: %s", query))
    loadQuery(query)
    return true
  }

  private fun hasPermission(permission: String): Boolean {
    val context: Context? = activity
    return PackageManager.PERMISSION_GRANTED == context!!.packageManager.checkPermission(
        permission, context.packageName
    )
  }

  private fun loadQuery(query: String?) {
    if (!TextUtils.isEmpty(query) && query != "nil") {
      mQuery = query
      Log.d(TAG, "Query is$query")
    }
  }

  fun setRecognitionListener() {
    Log.d(TAG, "==== setRecognitionListener in Fragment")
    if (DEBUG) {
      Log.d(
          TAG, "User is initiating a search. Do we have RECORD_AUDIO permission? " +
          hasPermission(Manifest.permission.RECORD_AUDIO)
      )
    }
    if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
      if (DEBUG) {
        Log.d(TAG, "Does not have RECORD_AUDIO, using SpeechRecognitionCallback")
      }
      Log.d(
          TAG, "==== Don't have permission setting recognition call back ," +
          " open intent"
      )
      setSpeechRecognitionCallback(SpeechRecognitionCallback {
        try {
          this@SearchFragment.startActivityForResult(recognizerIntent, REQUEST_SPEECH)
        } catch (e: ActivityNotFoundException) {
          Log.e(TAG, "Cannot find activity for speech recognizer", e)
        }
      })
    } else {
      setSpeechRecognizer()
      Log.d(TAG, "We DO have RECORD_AUDIO")
    }
  }

  companion object {
    private const val TAG = "SearchFragment"
    private const val DEBUG = true
    private const val FINISH_ON_RECOGNIZER_CANCELED = true
    private const val REQUEST_SPEECH = 0x00000010
  }
}