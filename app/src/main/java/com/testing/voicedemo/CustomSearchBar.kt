package com.testing.voicedemo

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.TextUtils
import android.util.AttributeSet
import android.util.Log
import android.util.SparseIntArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import androidx.core.content.res.ResourcesCompat
import androidx.leanback.widget.SearchEditText
import androidx.leanback.widget.SpeechRecognitionCallback

class CustomSearchBar @JvmOverloads constructor(
    private val mContext: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : RelativeLayout(
    mContext, attrs, defStyle
) {
  private var mSpeechRecognizer: SpeechRecognizer? = null
  private var mSpeechRecognitionCallback: SpeechRecognitionCallback? = null

  private lateinit var mSpeechOrbView: ImageView
  private lateinit var mSearchBarListener: SearchBarListener
  private lateinit var mSearchTextEditor: SearchEditText

  private var mCurrentLevel = 0

  var mSearchQuery: String
  private var mHint: String? = null
  private val mHandler = Handler()
  private var mAutoStartRecognition = false
  private val mBarHeight: Int

  private var mListening = false
  private var mSoundPool: SoundPool? = null
  private var mSoundMap = SparseIntArray()
  private var mRecognizing = false
  private var mAudioManager: AudioManager? = null
  private var mPermissionListener: SearchBarPermissionListener? = null

  override fun onFinishInflate() {
    super.onFinishInflate()
    val items = findViewById<View>(R.id.lb_search_bar_items) as RelativeLayout
    items.background
    mSearchTextEditor = findViewById<View>(R.id.lb_search_text_editor) as SearchEditText
    mSpeechOrbView = findViewById<View>(R.id.icon) as ImageView
    mSpeechOrbView.setOnClickListener { toggleRecognition() }
    mSpeechOrbView.setOnFocusChangeListener { v, hasFocus -> updateFocus(hasFocus) }
    updateUi()
    updateHint()
  }

  private fun updateFocus(hasFocus: Boolean) {
    if (DEBUG) Log.v(TAG, "SpeechOrb.onFocusChange $hasFocus")
    if (hasFocus) {
      if (mAutoStartRecognition) {
        startRecognition()
        mAutoStartRecognition = false
      }
    } else {
      stopRecognition()
    }
    updateUi()
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    if (DEBUG) Log.v(TAG, "Loading soundPool")
    mSoundPool = SoundPool(2, AudioManager.STREAM_SYSTEM, 0)
    loadSounds(mContext)
  }

  override fun onDetachedFromWindow() {
    stopRecognition()
    if (DEBUG) Log.v(TAG, "Releasing SoundPool")
    mSoundPool!!.release()
    super.onDetachedFromWindow()
  }

  /**
   * Sets a listener for when the term search changes
   *
   * @param listener
   */
  fun setSearchBarListener(listener: SearchBarListener) {
    mSearchBarListener = listener
  }

  /**
   * Sets the search query
   *
   * @param query the search query to use
   */
  fun setSearchQuery(query: String) {
    stopRecognition()
    mSearchTextEditor.setText(query)
    setSearchQueryInternal(query)
  }

  private fun setSearchQueryInternal(query: String) {
    if (DEBUG) Log.v(TAG, "setSearchQueryInternal $query")
    if (TextUtils.equals(mSearchQuery, query)) {
      return
    }
    mSearchQuery = query
    mSearchBarListener.onSearchQuerySubmit(mSearchQuery)
  }

  /**
   * Returns the current search bar hint text.
   */
  val hint: CharSequence?
    get() = mHint

  /**
   * Sets the speech recognition callback.
   *
   */
  @Deprecated(
      """Launching voice recognition activity is no longer supported. App should declare
      android.permission.RECORD_AUDIO in AndroidManifest file. See details in
      {@link androidx.leanback.app.SearchSupportFragment}."""
  )
  fun setSpeechRecognitionCallback(request: SpeechRecognitionCallback?) {
    Log.d(TAG, "==== custom search bar setSpeechRecognitionCallback$request")
    mSpeechRecognitionCallback = request
    check(!(mSpeechRecognitionCallback != null && mSpeechRecognizer != null)) { "Can't have speech recognizer and request" }
  }

  /**
   * This will update the hint for the search bar properly depending on state and provided title
   */
  private fun updateHint() {
    var title = resources.getString(R.string.lb_search_bar_hint)
    if (isVoiceMode) {
      title = resources.getString(R.string.lb_search_bar_hint_speech)
    }
    mHint = title
    mSearchTextEditor.hint = mHint
  }

  private fun toggleRecognition() {
    if (mRecognizing) {
      stopRecognition()
    } else {
      startRecognition()
    }
  }

  /**
   * Stops the speech recognition, if already started.
   */
  fun stopRecognition() {
    Log.e(TAG, "==== stopRecognition")
    Log.d(
        TAG,
        "==== stopRecognition mListening" + mListening + "mRecognizing " + mRecognizing
    )
    if (DEBUG) Log.v(
        TAG, String.format(
        "stopRecognition (listening: %s, recognizing: %s)",
        mListening, mRecognizing
    )
    )
    if (!mRecognizing) return

    // Edit text content was cleared when starting recognition; ensure the content is restored
    // in error cases
    mSearchTextEditor.setText(mSearchQuery)
    mSearchTextEditor.hint = mHint
    mRecognizing = false
    if (mSpeechRecognitionCallback != null || null == mSpeechRecognizer) return
    Log.e(TAG, "==== showNotListening")
    showNotListening()
    if (mListening) {
      mSpeechRecognizer!!.cancel()
      mListening = false
    }
    if (mSpeechRecognizer != null) {
      mAudioManager!!.abandonAudioFocus(null)
      mSpeechRecognizer!!.cancel()
      mSpeechRecognizer!!.stopListening()
      mSpeechRecognizer!!.destroy()
      mSpeechRecognizer!!.setRecognitionListener(null)
      mSpeechRecognizer = null
    }
  }

  fun showListening() {
    Log.d(TAG, "CustomSearchBar.showListening")
    mSpeechOrbView.setImageDrawable(
        ResourcesCompat.getDrawable(
            resources,
            R.drawable.voice_listening,
            null
        )
    )
    mListening = true
    mCurrentLevel = 0
  }

  private fun showNotListening() {
    Log.d(TAG, "CustomSearchBar.showNotListening")
    mSpeechOrbView.setImageDrawable(
        ResourcesCompat.getDrawable(
            resources,
            R.drawable.voice_search_selector,
            null
        )
    )
    mListening = false
  }

  fun startRecognition() {
    Log.e(TAG, "==== startRecognition")
    Log.d(TAG, "==== startRecognition mListening" + mListening + "mRecognizing " + mRecognizing + "mSpeechRecognizer" + mSpeechRecognizer)
    if (DEBUG) Log.v(
        TAG, String.format(
        "startRecognition (listening: %s, recognizing: %s)",
        mListening, mRecognizing
    )
    )
    if (mRecognizing) return
    if (!hasFocus()) {
      requestFocus()
    }
    if (mSpeechRecognitionCallback != null) {
      mSearchTextEditor.setText("")
      mSearchTextEditor.hint = ""
      mSpeechRecognitionCallback!!.recognizeSpeech()
      mRecognizing = true
      return
    }
    val res = context.checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO)
    if (PackageManager.PERMISSION_GRANTED != res) {
      if (Build.VERSION.SDK_INT >= 23 && mPermissionListener != null) {
        mPermissionListener!!.requestAudioPermission()
        return
      } else {
        throw IllegalStateException(
            Manifest.permission.RECORD_AUDIO
                + " required for search"
        )
      }
    }
    mRecognizing = true
    mSearchTextEditor.setText("")
    mAudioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val result = mAudioManager!!.requestAudioFocus(null, 3, 4)
    if (result == 1 && mSpeechRecognizer == null) {
      mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(mContext)
      val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
      recognizerIntent.putExtra(
          RecognizerIntent.EXTRA_LANGUAGE_MODEL,
          RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
      )
      recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
      recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, mContext.packageName)
      mSpeechRecognizer?.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(bundle: Bundle) {
          if (DEBUG) Log.v(TAG, "onReadyForSpeech")
          showListening()
          playSearchOpen()
        }

        override fun onBeginningOfSpeech() {
          if (DEBUG) Log.v(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
          val level = if (rmsdB < 0) 0 else (10 * rmsdB).toInt()
          setSoundLevel(level)
        }

        override fun onBufferReceived(bytes: ByteArray) {
          if (DEBUG) Log.v(TAG, "onBufferReceived " + bytes.size)
        }

        override fun onEndOfSpeech() {
          if (DEBUG) Log.v(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
          Log.e(TAG, "==== onError")
          if (DEBUG) Log.v(TAG, "onError $error")
          when (error) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> Log.w(
                TAG,
                "recognizer network timeout"
            )
            SpeechRecognizer.ERROR_NETWORK -> Log.w(TAG, "recognizer network error")
            SpeechRecognizer.ERROR_AUDIO -> Log.w(TAG, "recognizer audio error")
            SpeechRecognizer.ERROR_SERVER -> Log.w(TAG, "recognizer server error")
            SpeechRecognizer.ERROR_CLIENT -> Log.w(TAG, "recognizer client error")
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> Log.w(
                TAG,
                "recognizer speech timeout"
            )
            SpeechRecognizer.ERROR_NO_MATCH -> Log.w(TAG, "recognizer no match")
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> Log.w(TAG, "recognizer busy")
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> Log.w(
                TAG,
                "recognizer insufficient permissions"
            )
            else -> Log.d(TAG, "recognizer other error")
          }
          stopRecognition()
          playSearchFailure()
        }

        override fun onResults(bundle: Bundle) {
          if (DEBUG) Log.v(TAG, "onResults")
          val matches = bundle.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          if (matches != null) {
            if (DEBUG) Log.v(TAG, "Got results$matches")
            mSearchQuery = matches[0]
            mSearchTextEditor.setText(mSearchQuery)
            submitQuery()
          }
          stopRecognition()
          playSearchSuccess()
        }

        override fun onPartialResults(bundle: Bundle) {
          val results = bundle.getStringArrayList(
              SpeechRecognizer.RESULTS_RECOGNITION
          )
          if (DEBUG) {
            Log.v(
                TAG, "onPartialResults " + bundle + " results "
                + (results?.size ?: results)
            )
          }
          if (results == null || results.size == 0) {
            return
          }

          // stableText: high confidence text from PartialResults, if any.
          // Otherwise, existing stable text.
          val stableText = results[0]
          if (DEBUG) Log.v(TAG, "onPartialResults stableText $stableText")

          // pendingText: low confidence text from PartialResults, if any.
          // Otherwise, empty string.
          val pendingText = if (results.size > 1) results[1] else null
          if (DEBUG) Log.v(TAG, "onPartialResults pendingText $pendingText")
          mSearchTextEditor.updateRecognizedText(stableText, pendingText)
        }

        override fun onEvent(i: Int, bundle: Bundle) {}
      })
      mSpeechRecognizer?.startListening(recognizerIntent)
    }
  }

  private fun updateUi() {
    showNotListening()
    updateHint()
  }

  private val isVoiceMode: Boolean
    get() = mSpeechOrbView.isFocused

  fun submitQuery() {
    if (!TextUtils.isEmpty(mSearchQuery)) {
      mSearchBarListener.onSearchQuerySubmit(mSearchQuery)
    }
  }

  private fun loadSounds(context: Context) {
    val sounds = intArrayOf(
        R.raw.lb_voice_failure,
        R.raw.lb_voice_open,
        R.raw.lb_voice_no_input,
        R.raw.lb_voice_success
    )
    for (sound in sounds) {
      mSoundMap.put(sound, mSoundPool!!.load(context, sound, 1))
    }
  }

  private fun play(resId: Int) {
    mHandler.post {
      val sound = mSoundMap[resId]
      mSoundPool!!.play(
          sound, FULL_LEFT_VOLUME, FULL_RIGHT_VOLUME, DEFAULT_PRIORITY,
          DO_NOT_LOOP, DEFAULT_RATE
      )
    }
  }

  fun playSearchOpen() {
    play(R.raw.lb_voice_open)
  }

  fun playSearchFailure() {
    play(R.raw.lb_voice_failure)
  }

  fun playSearchSuccess() {
    play(R.raw.lb_voice_success)
  }

  override fun setNextFocusDownId(viewId: Int) {
    mSpeechOrbView.nextFocusDownId = viewId
    mSearchTextEditor.nextFocusDownId = viewId
  }

  /**
   * Sets the sound level while listening to speech.
   */
  fun setSoundLevel(level: Int) {
    if (!mListening) return

    // Either ease towards the target level, or decay away from it depending on whether
    // its higher or lower than the current.
    mCurrentLevel = if (level > mCurrentLevel) {
      mCurrentLevel + (level - mCurrentLevel) / 2
    } else {
      (mCurrentLevel * 0.7f).toInt()
    }
  }

  companion object {
    const val TAG = "CustomSearchBar"
    const val DEBUG = true
    const val FULL_LEFT_VOLUME = 1.0f
    const val FULL_RIGHT_VOLUME = 1.0f
    const val DEFAULT_PRIORITY = 1
    const val DO_NOT_LOOP = 0
    const val DEFAULT_RATE = 1.0f
  }

  init {
    val r = resources
    val inflater = LayoutInflater.from(context)
    inflater.inflate(R.layout.custom_search_bar, this, true)
    mBarHeight = resources.getDimensionPixelSize(R.dimen.lb_search_bar_height)
    val params = LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        mBarHeight
    )
    params.addRule(ALIGN_PARENT_TOP, TRUE)
    layoutParams = params
    setBackgroundColor(Color.TRANSPARENT)
    clipChildren = false
    mSearchQuery = ""
  }
}