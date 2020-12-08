package com.testing.voicedemo

/**
 * Interface that handles runtime permissions requests.
 * App sets listener on SearchBar via
 * [.setPermissionListener].
 */
interface SearchBarPermissionListener {
    fun requestAudioPermission()
}