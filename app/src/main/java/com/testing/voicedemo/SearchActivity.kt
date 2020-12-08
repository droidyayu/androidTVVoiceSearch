package com.testing.voicedemo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.fragment.app.FragmentActivity

class SearchActivity : FragmentActivity() {
  lateinit var mFragment: SearchFragment
  private val TAG = "SearchActivity"

  @RequiresApi(Build.VERSION_CODES.M)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_search)
    mFragment = (supportFragmentManager
        .findFragmentById(R.id.search_fragment) as SearchFragment)

    checkRunTimePermission()
  }

  private fun checkRunTimePermission() {
    Log.e(TAG, "==== checkRuntime Permission")

    val permissionArrays = arrayOf(Manifest.permission.RECORD_AUDIO)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      requestPermissions(permissionArrays, 11111)
    } else {
      Log.e(TAG, "==== checkRuntime Permission else")
      mFragment.setRecognitionListener()
    }
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<String?>,
      grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    Log.e(TAG, "==== OnPermission Result")

    if (11111 == requestCode && grantResults.isNotEmpty()) {
      if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Toast.makeText(this, "Granted", Toast.LENGTH_SHORT).show()
      }
      mFragment.setRecognitionListener()
    }
  }

}