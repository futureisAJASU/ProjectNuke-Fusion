package com.projectnuke.fusion.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.activity.compose.BackHandler
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectnuke.fusion.chat.ChatViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import java.io.File
import com.projectnuke.fusion.util.AttachmentStorageManager

@Composable
fun FusionApp() {
  val context = LocalContext.current
  val factory = remember(context) { ChatViewModel.factory(context) }
  val chatViewModel: ChatViewModel = viewModel(factory = factory)
  LaunchedEffect(chatViewModel) {
    CommittedDraftReconciliationDebtStore.retry(
      owner = DraftReconciliationOwner { debt ->
        val before = chatViewModel.draft(debt.draftKey)
        val release = before.activeSubmissionToken == null || before.activeSubmissionToken == debt.token
        DraftReconciliationResult(
          success = chatViewModel.draftMachine.reconcileCommittedSubmission(
          draftKey = debt.draftKey,
          token = debt.token,
          capturedRawInput = debt.capturedRawInput,
          committedPaths = debt.committedPaths,
          ),
          releasePaths = release,
        )
      },
      unregisterPath = AttachmentStorageManager::unregisterPendingAttachment,
      file = File(context.filesDir, "committed_draft_reconciliation_debt.json"),
    )
  }
  val currentConversationId by chatViewModel.currentConversationId.collectAsStateWithLifecycle()
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
  chatViewModel = chatViewModel,
  currentConversationId = currentConversationId,
                    onBack = {
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onOpenConversation = { conversationId ->
                        chatViewModel.selectConversation(conversationId)
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    onConversationRemovedFromList = { removedConversationId, nextConversationId ->
                        if (currentConversationId == removedConversationId) {
                            chatViewModel.selectConversation(nextConversationId ?: 0L)
                        }
                    },
                    onNewChat = {
                        chatViewModel.selectConversation(0L)
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
    chatViewModel.selectConversation(newId)
  },
  onOpenList = {
    scope.launch {
      drawerState.open()
    }
  },
  onNewChat = {
    chatViewModel.selectConversation(0L)
  },
  openModelLibraryRequest = openModelLibraryRequest.toInt(),
  openAdvancedSettingsRequest = openAdvancedSettingsRequest.toInt(),
  onOpenBenchmark = { modelName, openHistory ->
    benchmarkRequestModelFilter = modelName
    benchmarkRequestOpenHistory = openHistory
    openBenchmarkRequest += 1L
    scope.launch { drawerState.open() }
  },
  chatViewModel = chatViewModel
)
    }
}
