package com.testing.voicedemo

/**
 * Interface for receiving notification of search query changes.
 */
interface SearchBarListener {
    fun onSearchQuerySubmit(query: String?)
}