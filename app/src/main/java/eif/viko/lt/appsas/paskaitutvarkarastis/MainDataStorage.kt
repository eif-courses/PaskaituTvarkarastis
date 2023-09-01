package eif.viko.lt.appsas.paskaitutvarkarastis

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

class MainDataStorage(private val context: Context) {

    private val preferenceName: String = "DATASTORE_TEST"

    val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = preferenceName)

    /*
    * Read data from Datastore.
    * param key@String
    * return @String
    * */
    suspend fun readString(key: String): String? {
        val preferences = context.dataStore.data.first()
        return preferences[stringPreferencesKey(key)]//get data values with intPreferenceKey, booleanPreferenceKey.
    }

    /*
    * Write data to Datastore.
    * param key@String value@String
    * return
    * */
    suspend fun writeString(key: String, value: String?) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey(key)] = value!!//set data values with intPreferenceKey, booleanPreferenceKey.
        }
    }

    /*
    * Delete data from Datastore.
    * param key@String
    * return @String
    * */
    suspend fun removeString(key: String) {
        context.dataStore.edit { preferences ->
            preferences.remove(stringPreferencesKey(key))//delete data with intPreferenceKey, booleanPreferenceKey.
        }
    }

    /*
    * Remove all data from Datastore.
    * param
    * return
    * */
    suspend fun reset() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }


    companion object {

        private var INSTANCE: MainDataStorage? = null

        fun getInstance(context: Context): MainDataStorage {
            if (INSTANCE == null) {
                INSTANCE = MainDataStorage(context)
            }
            return INSTANCE!!
        }
    }

}