package com.projectnuke.fusion.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.projectnuke.fusion.data.AppDatabase
import com.projectnuke.fusion.data.ConversationEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DrawerBlackBg = Color(0xFF000000)
private val DrawerPanelBg = Color(0xFF171717)
private val DrawerCardBg = Color(0xFF111111)
private val DrawerCardSelectedBg = Color(0xFF202020)
private val DrawerLineColor = Color(0xFF2B2B2B)
private val DrawerTextPrimary = Color(0xFFF5F5F5)
private val DrawerTextSecondary = Color(0xFF9E9E9E)
private val DrawerAccentBlue = Color(0xFF9FD0FF)

@Composable
fun ConversationListScreen(
    onBack: () -> Unit,
    onOpenConversation: (Long) -> Unit,
    onNewChat: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val dao = remember { db.chatDao() }

    var searchQuery by remember { mutableStateOf("") }

    val conversations by dao.observeConversations()
        .collectAsState(initial = emptyList())

    val filteredConversations = remember(conversations, searchQuery) {
        if (searchQuery.isBlank()) {
            conversations
        } else {
            conversations.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DrawerBlackBg)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            DrawerTopBar(
                onClose = onBack,
                onNewChat = onNewChat
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            DrawerSearchBox(
                value = searchQuery,
                onValueChange = { searchQuery = it }
            )
        }

        item {
            Spacer(modifier = Modifier.height(6.dp))
            DrawerPrimaryButton(
                title = "새 채팅",
                subtitle = "빈 대화로 시작",
                leading = "+",
                onClick = onNewChat
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            DrawerSectionTitle("프로젝트")
        }

        item {
            DrawerActionRow(
                title = "새 프로젝트",
                subtitle = "대화, 파일, 메모리를 묶는 공간",
                leading = "P",
                onClick = {
                    Toast.makeText(context, "프로젝트 생성은 다음 단계에서 연결할게", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            DrawerActionRow(
                title = "프로젝트에 추가",
                subtitle = "현재 대화를 프로젝트에 묶기",
                leading = "↳",
                onClick = {
                    Toast.makeText(context, "프로젝트 추가 기능은 다음 단계에서 연결할게", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            DrawerSectionTitle("실험실")
        }

        item {
            DrawerActionRow(
                title = "모델 라이브러리",
                subtitle = "Gemma / Fusion / 커스텀 모델 관리",
                leading = "M",
                onClick = {
                    Toast.makeText(context, "모델 라이브러리는 LiteRT 연결 때 만들자", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            DrawerActionRow(
                title = "벤치마크",
                subtitle = "TTFT, 토큰 속도, 메모리 사용량 측정",
                leading = "B",
                onClick = {
                    Toast.makeText(context, "벤치마크 화면은 나중에 연결할게", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            DrawerActionRow(
                title = "Prompt Lab",
                subtitle = "프롬프트/시스템 프롬프트 테스트",
                leading = "L",
                onClick = {
                    Toast.makeText(context, "Prompt Lab은 나중에 연결할게", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            DrawerActionRow(
                title = "에이전트 모드",
                subtitle = "PokeClaw식 phone control 실험",
                leading = "A",
                onClick = {
                    Toast.makeText(context, "에이전트 모드는 Accessibility 붙일 때 연결하자", Toast.LENGTH_SHORT).show()
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            DrawerSectionTitle("대화")
        }

        if (filteredConversations.isEmpty()) {
            item {
                EmptyConversationList(
                    hasSearchQuery = searchQuery.isNotBlank()
                )
            }
        } else {
            items(
                items = filteredConversations,
                key = { it.id }
            ) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = {
                        onOpenConversation(conversation.id)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            DrawerBottomSettings(
                onSettings = {
                    Toast.makeText(context, "설정 화면은 다음 단계에서 만들자", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

@Composable
private fun DrawerTopBar(
    onClose: () -> Unit,
    onNewChat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawerCircleButton(
            text = "‹",
            onClick = onClose
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "Fusion",
                color = DrawerTextPrimary,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Local AI Workspace",
                color = DrawerTextSecondary,
                fontSize = 12.sp
            )
        }

        DrawerCircleButton(
            text = "+",
            onClick = onNewChat
        )
    }
}

@Composable
private fun DrawerSearchBox(
    value: String,
    onValueChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DrawerPanelBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "⌕",
                color = DrawerTextSecondary,
                fontSize = 19.sp
            )

            Spacer(modifier = Modifier.width(10.dp))

            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = TextStyle(
                    color = DrawerTextPrimary,
                    fontSize = 15.sp
                ),
                decorationBox = { innerTextField ->
                    if (value.isBlank()) {
                        Text(
                            text = "대화 검색",
                            color = DrawerTextSecondary,
                            fontSize = 15.sp
                        )
                    }
                    innerTextField()
                }
            )
        }
    }
}

@Composable
private fun DrawerPrimaryButton(
    title: String,
    subtitle: String,
    leading: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        color = DrawerPanelBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerAvatar(
                text = leading,
                accent = true
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = DrawerTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = subtitle,
                    color = DrawerTextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun DrawerActionRow(
    title: String,
    subtitle: String,
    leading: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(15.dp),
        color = DrawerBlackBg
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerAvatar(
                text = leading,
                accent = false
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    color = DrawerTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = subtitle,
                    color = DrawerTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ConversationEntity,
    onClick: () -> Unit
) {
    val dateText = remember(conversation.updatedAt) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
            .format(Date(conversation.updatedAt))
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(15.dp),
        color = DrawerCardBg
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DrawerAvatar(
                text = conversation.title
                    .trim()
                    .firstOrNull()
                    ?.uppercaseChar()
                    ?.toString()
                    ?: "F",
                accent = false
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = conversation.title.ifBlank { "새 대화" },
                    color = DrawerTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = dateText,
                    color = DrawerTextSecondary,
                    fontSize = 11.sp
                )
            }

            Text(
                text = "›",
                color = DrawerTextSecondary,
                fontSize = 22.sp
            )
        }
    }
}

@Composable
private fun DrawerBottomSettings(
    onSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(DrawerLineColor)
    )

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onSettings() }
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DrawerAvatar(
            text = "S",
            accent = false
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = "설정",
                color = DrawerTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = "모델, 메모리, 성능, 개인정보",
                color = DrawerTextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DrawerSectionTitle(
    text: String
) {
    Text(
        text = text,
        color = DrawerTextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun DrawerAvatar(
    text: String,
    accent: Boolean
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (accent) DrawerCardSelectedBg else DrawerPanelBg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.take(2),
            color = if (accent) DrawerAccentBlue else DrawerTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyConversationList(
    hasSearchQuery: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = if (hasSearchQuery) "검색 결과가 없어." else "아직 저장된 대화가 없어.",
            color = DrawerTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (hasSearchQuery) {
                "다른 키워드로 검색해봐."
            } else {
                "새 채팅을 시작하면 여기에 저장돼."
            },
            color = DrawerTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun DrawerCircleButton(
    text: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(DrawerPanelBg)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = DrawerTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium
        )
    }
}