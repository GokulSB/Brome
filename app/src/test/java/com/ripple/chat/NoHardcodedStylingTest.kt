package com.ripple.chat

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NoHardcodedStylingTest {
    private val forbidden = listOf(
        Regex("""RoundedCornerShape\(\s*\d+\.dp"""),
        Regex("""fontFamily\s*="""),
        Regex("""fontSize\s*=\s*\d+(\.\d+)?\.sp"""),
        Regex("""Color\(0x[0-9A-Fa-f]+\)""")
    )

    private val allowedFiles = setOf(
        "BromeShapes.kt",
        "BromeTypography.kt",
        "BromeCategoryColors.kt",
        "BromeBackground.kt",
        "Type.kt",
        "Theme.kt",
        "ThemeSettings.kt",
        "VariantBTheme.kt",
        "SettingsScreen.kt",
        "SettingsSelfTestScreen.kt",
        "FinanceCategorizer.kt",
        "Color.kt",
        "OtpBannerCard.kt",
        "ExpressiveComponents.kt",
        "VariantBComponents.kt",
        "MessageCard.kt",
        "PieChartDialog.kt",
        "TutorialOverlay.kt",
        "ArchiveScreen.kt",
        "ChatDetailScreen.kt",
        "ChatsScreen.kt",
        "NewMessageScreen.kt",
        "OtpScreen.kt",
        "StarredScreen.kt",
        "BrutalistBox.kt",
        "TideComponents.kt",
        "ContactsScreen.kt",
        "FinanceLedgerScreen.kt",
        "OnboardingScreen.kt",
        "ProfileScreen.kt",
        "ThemeScreen.kt",
        "TrashScreen.kt",
        "AppStrings.kt",
        "IslandModels.kt",
        "DynamicIslandUi.kt",
        "DynamicIslandService.kt",
        "DynamicIslandSettings.kt",
        "DynamicIslandSettingsScreen.kt",
        "IslandNotifier.kt",
        "IslandBanner.kt",
        "IslandQueueManager.kt",
        "IslandActions.kt",
        "BromeToast.kt",
        "BromeEmojiPicker.kt",
        "SendProbeScreen.kt",
        "AutoDeleteManager.kt"
    )

    @Test
    fun noHardcodedStyling() {
        val rootDir = File("src/main/java")
        if (!rootDir.exists()) return

        val hits = rootDir.walkTopDown()
            .filter { it.extension == "kt" && it.name !in allowedFiles }
            .flatMap { f ->
                f.readLines().withIndex().filter { (_, l) ->
                    forbidden.any { it.containsMatchIn(l) }
                }.map { "${f.name}:${it.index + 1}: ${it.value.trim()}" }
            }
            .toList()

        assertTrue("Hardcoded styling found:\n" + hits.joinToString("\n"), hits.isEmpty())
    }
}
