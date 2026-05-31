package com.example.expenditure.ui

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate

internal actual fun currentLocalDate(): LocalDate = java.time.LocalDate.now().toKotlinLocalDate()
