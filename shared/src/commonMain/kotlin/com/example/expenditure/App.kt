package com.example.expenditure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.expenditure.analysis.ExpenditureAnalysis
import com.example.expenditure.db.ExpenditureRepository
import com.example.expenditure.ui.BankTable
import com.example.expenditure.ui.DateFilterBar
import com.example.expenditure.ui.TypeManagerDialog
import com.example.expenditure.ui.ExpenditureViewModel
import com.example.expenditure.ui.ReweTable
import com.example.expenditure.ui.theme.ExpenditureTheme

private enum class Tabs(val label: String) { BANK("Banks"), REWE("Rewe") }

@Composable
fun App(repository: ExpenditureRepository) {
    ExpenditureTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val viewModel = remember { ExpenditureViewModel(ExpenditureAnalysis(repository)) }
            LaunchedEffect(Unit) {
                viewModel.loadRewe()
                viewModel.loadBank()
            }

            var selectedTab by remember { mutableStateOf(Tabs.BANK) }

            // Popup visibility is hoisted into the ViewModel so the trigger button (now in the
            // table pane) and this dialog can live in separate composables.
            if (viewModel.showTypeManager) {
                TypeManagerDialog(
                    categoryTypes = viewModel.categoryTypes,
                    onDismiss = { viewModel.dismissTypeManager() },
                    onSave = { edits ->
                        viewModel.saveCategoryTypes(edits)
                        viewModel.dismissTypeManager()
                    },
                )
            }

            Column(Modifier.fillMaxSize().safeContentPadding()) {
                Text(
                    text = "Expenditure tracker",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp),
                )
                DateFilterBar(viewModel, Modifier.fillMaxWidth())
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tabs.entries.forEach { tab ->
                        Tab(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            text = { Text(tab.label) },
                        )
                    }
                }
                when (selectedTab) {
                    Tabs.REWE -> ReweTable(viewModel, Modifier.fillMaxSize())
                    Tabs.BANK -> BankTable(viewModel, Modifier.fillMaxSize())
                }
            }
        }
    }
}
