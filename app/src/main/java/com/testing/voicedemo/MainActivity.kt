package com.testing.voicedemo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

class MainActivity : Activity() {
  lateinit var button: Button
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)
    button = findViewById(R.id.dummy)
    button.setOnClickListener {
      startActivity(Intent(this, SearchActivity::class.java))
    }
  }
}