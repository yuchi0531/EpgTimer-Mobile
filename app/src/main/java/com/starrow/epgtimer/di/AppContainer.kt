package com.starrow.epgtimer.di

import android.content.Context
import com.starrow.epgtimer.data.repository.EpgRepository
import com.starrow.epgtimer.data.repository.EpgRepositoryImpl
import com.starrow.epgtimer.data.repository.SharedPreferencesStore

class AppContainer(context: Context) {
    val repository: EpgRepository = EpgRepositoryImpl(SharedPreferencesStore(context))
}
