package com.testing.voicedemo

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.speech.RecognizerIntent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import androidx.leanback.widget.SpeechRecognitionCallback

open class CustomSearchSupportFragment : Fragment() {
  private lateinit var mSearchBar: CustomSearchBar
  private lateinit var mProvider: SearchResultProvider
  private var mSpeechRecognitionCallback: SpeechRecognitionCallback? = null
  private var mExternalQuery: ExternalQuery? = null
  private var mStatus = 0
  private var mAutoStartRecognition = true
  private var mIsPaused = false
  private var mPendingStartRecognitionWhenPaused = false
  open val mHandler = Handler()

  private val mSetSearchResultProvider = Runnable {
    if (mAutoStartRecognition) {
      mHandler.removeCallbacks(mStartRecognitionRunnable)
      mHandler.postDelayed(mStartRecognitionRunnable, SPEECH_RECOGNITION_DELAY_MS)
    }
  }

  private val mStartRecognitionRunnable = Runnable {
    mAutoStartRecognition = false
    mSearchBar.startRecognition()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    if (mAutoStartRecognition) {
      mAutoStartRecognition = savedInstanceState == null
    }
    super.onCreate(savedInstanceState)
  }

  override fun onCreateView(
      inflater: LayoutInflater, container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View? {
    val root = inflater.inflate(R.layout.custom_search_support_fragment, container, false)
    val searchFrame = root.findViewById<View>(R.id.lb_search_frame) as FrameLayout
    mSearchBar = searchFrame.findViewById<View>(R.id.lb_search_bar) as CustomSearchBar
    mSearchBar.setSearchBarListener(object : SearchBarListener {
      override fun onSearchQuerySubmit(query: String?) {
        if (DEBUG) Log.v(TAG, String.format("onSearchQuerySubmit %s", query))
        submitQuery(query)
      }
    })
    return root
  }

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    mSearchBar.setSpeechRecognitionCallback(mSpeechRecognitionCallback)
    applyExternalQuery()
    onSetSearchResultProvider()
  }

  override fun onResume() {
    super.onResume()
    mIsPaused = false
  }

  fun setSpeechRecognizer() {
    Log.d(TAG, "==== have permission will set Speech Recognizer")
    if (mPendingStartRecognitionWhenPaused) {
      mPendingStartRecognitionWhenPaused = false
      mSearchBar.startRecognition()
    } else {
      // Ensure search bar state consistency when using external recognizer
      mSearchBar.stopRecognition()
    }
  }

  override fun onPause() {
    mIsPaused = true
    super.onPause()
  }

  /**
   * Sets the search provider that is responsible for returning results for the
   * search query.
   */
  fun setSearchResultProvider(searchResultProvider: SearchResultProvider) {
    if (!this::mProvider.isInitialized || mProvider !== searchResultProvider) {
      mProvider = searchResultProvider
      onSetSearchResultProvider()
    }
  }

  @Deprecated(
      """Launching voice recognition activity is no longer supported. App should declare
      android.permission.RECORD_AUDIO in AndroidManifest file."""
  )
  fun setSpeechRecognitionCallback(callback: SpeechRecognitionCallback?) {
    Log.e(TAG, "==== setSpeechRecognitionCallback callback$callback")
    mSpeechRecognitionCallback = callback
    mSearchBar.setSpeechRecognitionCallback(mSpeechRecognitionCallback!!)
    if (callback != null) {
      mSearchBar.stopRecognition()
    }
  }

  /**
   * Sets the text of the search query and optionally submits the query. Either
   * [onQueryTextChange][androidx.leanback.app.SearchSupportFragment.SearchResultProvider.onQueryTextChange] or
   * [onQueryTextSubmit][androidx.leanback.app.SearchSupportFragment.SearchResultProvider.onQueryTextSubmit] will be
   * called on the provider if it is set.
   *
   * @param query  The search query to set.
   * @param submit Whether to submit the query.
   */
  private fun setSearchQuery(query: String?, submit: Boolean) {
    if (DEBUG) Log.v(TAG, "setSearchQuery $query submit $submit")
    if (query == null) {
      return
    }
    mExternalQuery = ExternalQuery(query, submit)
    applyExternalQuery()
    if (mAutoStartRecognition) {
      mAutoStartRecognition = false
      mHandler.removeCallbacks(mStartRecognitionRunnable)
    }
  }

  /**
   * Sets the text of the search query based on the [RecognizerIntent.EXTRA_RESULTS] in
   * the given intent, and optionally submit the query.  If more than one result is present
   * in the results list, the first will be used.
   *
   * @param intent Intent received from a speech recognition service.
   * @param submit Whether to submit the query.
   */
  fun setSearchQuery(intent: Intent, submit: Boolean) {
    val matches = intent.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
    if (matches != null && matches.size > 0) {
      setSearchQuery(matches[0], submit)
    }
  }

  val recognizerIntent: Intent
    get() {
      val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
      recognizerIntent.putExtra(
          RecognizerIntent.EXTRA_LANGUAGE_MODEL,
          RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
      )
      recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      if (mSearchBar.hint != null) {
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, mSearchBar!!.hint)
      }
      return recognizerIntent
    }

  fun submitQuery(query: String?) {
    queryComplete()
    mProvider.onQueryTextSubmit(query)
  }

  private fun queryComplete() {
    if (DEBUG) Log.v(TAG, "queryComplete")
    mStatus = mStatus or QUERY_COMPLETE
  }

  private fun onSetSearchResultProvider() {
    mHandler.removeCallbacks(mSetSearchResultProvider)
    mHandler.post(mSetSearchResultProvider)
  }

  private fun applyExternalQuery() {
    if (mExternalQuery == null) {
      return
    }
    mSearchBar.setSearchQuery(mExternalQuery!!.mQuery)
    if (mExternalQuery!!.mSubmit) {
      submitQuery(mExternalQuery!!.mQuery)
    }
    mExternalQuery = null
  }

  companion object {
    val TAG = CustomSearchSupportFragment::class.java.simpleName
    const val DEBUG = true
    const val SPEECH_RECOGNITION_DELAY_MS: Long = 300
    const val QUERY_COMPLETE = 0x2
  }

  class ExternalQuery(var mQuery: String, var mSubmit: Boolean)

}