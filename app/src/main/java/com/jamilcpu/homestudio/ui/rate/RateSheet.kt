package com.jamilcpu.homestudio.ui.rate

import androidx.compose.runtime.Composable
import com.jamilcpu.homestudio.data.api.HomeStudioApi

data class RateActions(
    val onLiked: (Boolean) -> Unit = {},
    val onSatisfaction: (Int) -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onSave: () -> Unit = {},
)

@Composable
fun RateSheetHost(controller: RateSheetController, api: HomeStudioApi, onRated: () -> Unit) = Unit

@Composable
fun RateSheetContent(title: String?, state: RateUiState, actions: RateActions) = Unit
