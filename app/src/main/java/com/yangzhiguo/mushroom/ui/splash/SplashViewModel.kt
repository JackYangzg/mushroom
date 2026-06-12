package com.yangzhiguo.mushroom.ui.splash

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yangzhiguo.mushroom.data.seed.SeedDataInitializer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val seed: SeedDataInitializer,
) : ViewModel() {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        _state.value = State.Loading
        viewModelScope.launch {
            try {
                seed.ensureSeeded()
                val onboarded = prefs.getBoolean(KEY_ONBOARDED, false)
                val firstLaunch = !onboarded
                _state.value = State.Ready(firstLaunch = firstLaunch)
            } catch (t: Throwable) {
                _state.value = State.Error(t.message ?: "Unknown error")
            }
        }
    }

    fun markOnboardingComplete() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
    }

    sealed class State {
        data object Loading : State()
        data class Ready(val firstLaunch: Boolean) : State()
        data class Error(val message: String) : State()
    }

    companion object {
        private const val PREFS_NAME = "mushroom_prefs"
        private const val KEY_ONBOARDED = "onboarded"
    }
}
