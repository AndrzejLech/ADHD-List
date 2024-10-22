package com.example.adhdlist.presentation.base

import android.content.Context
import androidx.lifecycle.ViewModel

abstract class BaseViewModel: ViewModel() {
    abstract val TAG: String

    abstract fun triggerCommand(command: BaseCommand)
    abstract fun handleAction(action: BaseAction)
    abstract fun handleError(context: Context, error: BaseError)

    open class BaseUiState
    open class BaseCommand
    open class BaseAction
    open class BaseError
}