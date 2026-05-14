package com.projectnuke.fusion.ui

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun FusionApp() {
    var currentConversationId by remember { mutableLongStateOf(0L) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(340.dp),
                drawerContainerColor = Color(0xFF000000),
                drawerContentColor = Color(0xFFF5F5F5)
            ) {
                ConversationListScreen(
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
                    onNewChat = {
                        currentConversationId = 0L
                        scope.launch {
                            drawerState.close()
                        }
                    }
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
            }
        )
    }
}