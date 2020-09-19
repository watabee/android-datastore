/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.codelab.android.datastore.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.datastore.DataStore
import androidx.datastore.createDataStore
import androidx.datastore.migrations.SharedPreferencesMigration
import androidx.datastore.migrations.SharedPreferencesView
import com.codelab.android.datastore.UserPreferences
import com.codelab.android.datastore.UserPreferences.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import java.io.IOException

private const val USER_PREFERENCES_NAME = "user_preferences"
private const val SORT_ORDER_KEY = "sort_order"

/**
 * Class that handles saving and retrieving user preferences
 */
class UserPreferencesRepository(context: Context) {

    companion object {
        private const val TAG: String = "UserPreferencesRepo"
    }

    private val sharedPrefsMigration = SharedPreferencesMigration(
        context,
        USER_PREFERENCES_NAME
    ) { sharedPrefs: SharedPreferencesView, currentData: UserPreferences ->
        // Define the mapping from SharedPreferences to UserPreferences
        if (currentData.sortOrder == SortOrder.UNSPECIFIED) {
            currentData.toBuilder().setSortOrder(
                SortOrder.valueOf(sharedPrefs.getString(SORT_ORDER_KEY, SortOrder.NONE.name)!!)
            ).build()
        } else {
            currentData
        }
    }

    private val dataStore: DataStore<UserPreferences> =
        context.createDataStore(
            fileName = "user_prefs.pb",
            serializer = UserPreferencesSerializer,
            migrations = listOf(sharedPrefsMigration)
        )

    val userPreferencesFlow: Flow<UserPreferences> = dataStore.data
        .catch { exception ->
            // dataStore.data throws an IOException when an error is encountered when reading data
            if (exception is IOException) {
                Log.e(TAG, "Error reading sort order preferences.", exception)
                emit(UserPreferences.getDefaultInstance())
            } else {
                throw exception
            }
        }

    suspend fun updateShowCompleted(completed: Boolean) {
        dataStore.updateData { preferences ->
            preferences.toBuilder().setShowCompleted(completed).build()
        }
    }

    suspend fun enableSortByDeadline(enable: Boolean) {
        dataStore.updateData { preferences ->
            val currentOrder = preferences.sortOrder
            val newSortOrder =
                if (enable) {
                    if (currentOrder == SortOrder.BY_PRIORITY) {
                        SortOrder.BY_DEADLINE_AND_PRIORITY
                    } else {
                        SortOrder.BY_DEADLINE
                    }
                } else {
                    if (currentOrder == SortOrder.BY_DEADLINE_AND_PRIORITY) {
                        SortOrder.BY_PRIORITY
                    } else {
                        SortOrder.NONE
                    }
                }
            preferences.toBuilder().setSortOrder(newSortOrder).build()
        }
    }

    suspend fun enableSortByPriority(enable: Boolean) {
        dataStore.updateData { preferences ->
            val currentOrder = preferences.sortOrder
            val newSortOrder =
                if (enable) {
                    if (currentOrder == SortOrder.BY_DEADLINE) {
                        SortOrder.BY_DEADLINE_AND_PRIORITY
                    } else {
                        SortOrder.BY_PRIORITY
                    }
                } else {
                    if (currentOrder == SortOrder.BY_DEADLINE_AND_PRIORITY) {
                        SortOrder.BY_DEADLINE
                    } else {
                        SortOrder.NONE
                    }
                }
            preferences.toBuilder().setSortOrder(newSortOrder).build()
        }
    }
}
