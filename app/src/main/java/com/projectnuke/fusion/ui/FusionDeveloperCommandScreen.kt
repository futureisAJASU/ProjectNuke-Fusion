package com.projectnuke.fusion.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DevCmdPanelBg = Color(0xFF171717)
private val DevCmdCardBg = Color(0xFF111111)
private val DevCmdTextPrimary = Color(0xFFF5F5F5)
private val DevCmdTextSecondary = Color(0xFF9E9E9E)
private val DevCmdAccentBlue = Color(0xFF9FD0FF)

private data class DevCommandItem(
    val title: String,
    val command: String
)

private data class DevCommandCategory(
    val title: String,
    val items: List<DevCommandItem>
)

@Composable
fun FusionDeveloperCommandDialog(
    context: Context,
    clipboard: ClipboardManager,
    onDismiss: () -> Unit
) {
    val categories = remember { buildFusionDeveloperCommandCategories() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("개발 명령어") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DevCommandHeaderCard("개발 명령어", "Fusion 개발 중 자주 사용하는 명령어를 복사합니다.")
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { category ->
                        item {
                            DevCommandCategoryCard(
                                category = category,
                                onCopy = { command ->
                                    clipboard.setText(AnnotatedString(command))
                                    Toast.makeText(context, "명령어를 복사했습니다.", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                clipboard.setText(AnnotatedString(buildAllDeveloperCommandsText(categories)))
                Toast.makeText(context, "개발 명령어를 복사했습니다.", Toast.LENGTH_SHORT).show()
            }) { Text("전체 명령어 복사", color = DevCmdAccentBlue) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("닫기", color = DevCmdTextSecondary) }
        },
        containerColor = DevCmdPanelBg,
        titleContentColor = DevCmdTextPrimary,
        textContentColor = DevCmdTextPrimary
    )
}

@Composable
private fun DevCommandHeaderCard(title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DevCmdCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, color = DevCmdTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(body, color = DevCmdTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DevCommandCategoryCard(
    category: DevCommandCategory,
    onCopy: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = DevCmdCardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(category.title, color = DevCmdTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            category.items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            item.title,
                            color = DevCmdTextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        TextButton(onClick = { onCopy(item.command) }) {
                            Text("복사", color = DevCmdAccentBlue, fontSize = 12.sp)
                        }
                    }
                    Text(
                        item.command,
                        color = DevCmdTextPrimary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

private fun buildFusionDeveloperCommandCategories(): List<DevCommandCategory> {
    return listOf(
        DevCommandCategory(
            "빌드",
            listOf(
                DevCommandItem(
                    "Debug APK 빌드",
                    "cd \"C:\\Users\\junyo\\AndroidStudioProjects\\Fusion\"\n.\\gradlew assembleDebug"
                ),
                DevCommandItem(
                    "클린 빌드",
                    ".\\gradlew clean assembleDebug"
                )
            )
        ),
        DevCommandCategory(
            "ADB 설치",
            listOf(
                DevCommandItem("연결 기기 확인", "adb devices"),
                DevCommandItem(
                    "Debug APK 설치",
                    "adb install -r \"app\\build\\outputs\\apk\\debug\\app-debug.apk\""
                ),
                DevCommandItem(
                    "platform-tools 경로로 설치",
                    "& \"\$env:LOCALAPPDATA\\Android\\Sdk\\platform-tools\\adb.exe\" install -r \"app\\build\\outputs\\apk\\debug\\app-debug.apk\""
                ),
                DevCommandItem(
                    "빌드 후 바로 설치",
                    ".\\gradlew assembleDebug; & \"\$env:LOCALAPPDATA\\Android\\Sdk\\platform-tools\\adb.exe\" install -r \"app\\build\\outputs\\apk\\debug\\app-debug.apk\""
                )
            )
        ),
        DevCommandCategory(
            "로그",
            listOf(
                DevCommandItem(
                    "최근 크래시 로그",
                    "adb logcat -d -t 1000 | Select-String -Pattern \"FATAL EXCEPTION|AndroidRuntime|projectnuke|Fusion\" -Context 5,40"
                ),
                DevCommandItem(
                    "Fusion 관련 로그",
                    "adb logcat -d -t 1000 | Select-String -Pattern \"projectnuke|Fusion|LiteRt|Benchmark|Memory|A/B\" -Context 3,20"
                ),
                DevCommandItem(
                    "실시간 로그 보기",
                    "adb logcat | Select-String -Pattern \"projectnuke|Fusion|AndroidRuntime\""
                )
            )
        ),
        DevCommandCategory(
            "Git",
            listOf(
                DevCommandItem("상태 확인", "git status"),
                DevCommandItem(
                    "커밋",
                    "git add .\ngit commit -m \"Update Fusion\"\ngit push"
                ),
                DevCommandItem(
                    "태그 생성",
                    "git tag v0.4.9-alpha\ngit push origin v0.4.9-alpha"
                )
            )
        ),
        DevCommandCategory(
            "검색",
            listOf(
                DevCommandItem(
                    "버전 확인",
                    "Select-String -Path \"app\\build.gradle*\" -Pattern \"versionCode|versionName\""
                ),
                DevCommandItem(
                    "깨진 한글 검색",
                    "Select-String -Path \"app\\src\\main\\java\\com\\projectnuke\\fusion\\**\\*.kt\" -Pattern \"�|媛|쒕|濡|蹂|湲|嫄|瑗|\\?\\?\""
                ),
                DevCommandItem(
                    "위험 설정 검색",
                    "Select-String -Path \"app\\src\\main\\java\\com\\projectnuke\\fusion\\**\\*.kt\" -Pattern \"fallbackToDestructiveMigration|TODO|throw NotImplementedError\""
                )
            )
        )
    )
}

private fun buildAllDeveloperCommandsText(categories: List<DevCommandCategory>): String {
    return buildString {
        appendLine("Fusion 개발 명령어")
        appendLine()
        categories.forEach { category ->
            appendLine("[${category.title}]")
            category.items.forEach { item ->
                appendLine("- ${item.title}")
                appendLine(item.command)
                appendLine()
            }
        }
    }.trimEnd()
}
