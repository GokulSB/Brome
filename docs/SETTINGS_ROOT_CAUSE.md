# Brome — Settings Working Path & Root Cause Trace (Step 1)

## 1. Trace of the Working Path (Background Design)

- **Settings Control**: `BackgroundSettingsContent` in `SettingsScreen.kt` line 1605:
  `onClick = { coroutineScope.launch { bromeSettings.setBackgroundDesign(design) } }`
- **Function Called**: `bromeSettings.setBackgroundDesign(design)` in `BromeSettings.kt` line 94:
  `suspend fun setBackgroundDesign(design: BackgroundDesign) = write("background_design", design) { it[K.BACKGROUND_DESIGN] = design.name }`
- **Store / Key**: `Context.bromeDataStore` (top-level DataStore in `BromeSettings.kt`), key `K.BACKGROUND_DESIGN` = `stringPreferencesKey("brome_background_design")`.
- **Flow**: `bromeSettings.appearanceFlow` in `BromeSettings.kt` line 77 maps `bromeDataStore.data` to `BromeAppearance` data class.
- **Where Collected**: `MainActivity.kt` line 86:
  `val appearance by bromeSettings.appearanceFlow.collectAsState(initial = BromeAppearance())`
- **Where Applied**: `MainActivity.kt` line 97:
  `BromeBackground(design = appearance.backgroundDesign, seed = appearance.backgroundSeed, washLevel = appearance.gradientWashLevel) { ... }`

`BromeBackground` is rendered at the root of `MainActivity` wrapping the navigation host. When `appearanceFlow` emits a new value, `MainActivity` recomposes and `BromeBackground` immediately updates the background canvas.

## 2. Why the Other Settings Did Not Take Effect

1. **Theme / Colors**: `BromeTheme` in `Theme.kt` was reading theme parameters from `ThemeSettings` (`ThemeConfig`), whereas `SettingsScreen` was writing color and mode choices to `BromeSettings` (`BromeAppearance`). Furthermore, UI components used fixed color objects (`VariantBPeach`, `VariantBYellow`, `VariantBGreen`) rather than `MaterialTheme.colorScheme.primary` or `MaterialTheme.colorScheme.tertiary`.
2. **Corner Radius & Sharp Corner**: `getTideCornerShape` helper in `BromeCategoryColors.kt` defaulted to `baseRadius = 30.dp` and `sharpRadius = 10.dp` without reading `LocalBromeAppearance.current`. Cards across screens called `getTideCornerShape(index)` without passing the user's chosen radius.
3. **Typeface & Text Size**: Composable `Text(...)` calls contained explicit `fontFamily = BricolageFontFamily` and `fontFamily = FigtreeFontFamily` parameters that overrode `MaterialTheme.typography` font families.
4. **Chat Bubbles**: `VariantBChatBubble` shape calculations used hardcoded `28.dp` corner values instead of deriving shapes from `LocalBromeBubbles.current`.
5. **Nav Pill & FAB Sizes**: `TideFloatingNavAndFab` read nav height and FAB size, but list bottom content paddings across screens used hardcoded `120.dp` values instead of `LocalBromeLayout.current.navPill + 40.dp`.
6. **Search Hint**: Search pills on Contacts, Starred, and Archive screens contained hardcoded placeholder strings instead of reading `LocalBromeLayout.current.searchHint`.

## 3. Root Cause Summary
Background design worked because its pipeline was complete from `BromeSettings` -> `MainActivity` -> `BromeBackground`. Other settings failed because: (1) `ThemeSettings` and `BromeSettings` maintained separate preferences streams, and (2) UI components contained hardcoded `RoundedCornerShape(...)`, explicit `fontFamily` parameters on `Text(...)`, static color constants, and hardcoded `dp` padding values that bypassed composition local tokens (`LocalBromeAppearance`, `LocalBromeShapes`, `LocalBromeBubbles`, `LocalBromeLayout`, `LocalDensity`). By unifying settings into `BromeSettingsRepository` and using dynamic token references everywhere, all settings now update live across all screens.
