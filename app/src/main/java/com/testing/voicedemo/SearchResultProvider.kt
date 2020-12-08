package com.testing.voicedemo

/**
 * Search API to be provided by the application.
 */
interface SearchResultProvider {
    fun onQueryTextSubmit(query: String?): Boolean
}