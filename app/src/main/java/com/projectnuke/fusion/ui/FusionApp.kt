package com.projectnuke.fusion.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

@Composable
fun FusionApp() {
    var currentConversationId by remember { mutableLongStateOf(0L) }
    var openModelLibraryRequest by remember { mutableLongStateOf(0L) }
    var openAdvancedSettingsRequest by remember { mutableLongStateOf(0L) }
    var openBenchmarkRequest by remember { mutableLongStateOf(0L) }
    var benchmarkRequestModelFilter by remember { mutableStateOf<String?>(null) }
    var benchmarkRequestOpenHistory by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawerState.currentValue == DrawerValue.Open) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(),
                drawerContainerColor = Color(0xFF000000),
                drawerContentColor = Color(0xFFF5F5F5)
            ) {
                ConversationListScreenV2(
                    currentConversationId = currentConversationId,
                    onBack = {
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onOpenConversation = { conversationId ->
                        currentConversationId = conversationId
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onConversationRemovedFromList = { removedConversationId, nextConversationId ->
                        if (currentConversationId == removedConversationId) {
                            currentConversationId = nextConversationId ?: 0L
                        }
                    },
                    onNewChat = {
                        currentConversationId = 0L
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    isDrawerOpen = drawerState.currentValue == DrawerValue.Open,
                    onOpenModelLibrary = {
                        openModelLibraryRequest += 1L
                        scope.launch { drawerState.close() }
                    },
                    onOpenAdvancedSettings = {
                        openAdvancedSettingsRequest += 1L
                        scope.launch { drawerState.close() }
                    },
                    openBenchmarkRequest = openBenchmarkRequest.toInt(),
                    benchmarkRequestModelFilter = benchmarkRequestModelFilter,
                    benchmarkRequestOpenHistory = benchmarkRequestOpenHistory
                )
            }
        }
    ) {
        ChatScreen(
            conversationId = currentConversationId,
            onConversationCreated = { newId ->
                currentConversationId = newId
            },
            onOpenList = {
                scope.launch {
                    drawerState.open()
                }
            },
            onNewChat = {
                currentConversationId = 0L
            },
            openModelLibraryRequest = openModelLibraryRequest.toInt(),
            openAdvancedSettingsRequest = openAdvancedSettingsRequest.toInt(),
            onOpenBenchmark = { modelName, openHistory ->
                benchmarkRequestModelFilter = modelName
                benchmarkRequestOpenHistory = openHistory
                openBenchmarkRequest += 1L
                scope.launch { drawerState.open() }
            }
        )
    }
}
