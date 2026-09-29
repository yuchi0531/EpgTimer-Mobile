package com.starrow.epgtimer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

fun <T : ViewModel> vmFactory(create: () -> T): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = create() as VM
    }
