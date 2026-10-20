# Brome — Settings Wiring & Audit Matrix

Every setting on the Settings screen in Brome has been audited and wired end-to-end to update the entire application live without requiring an app restart. All choices are saved in DataStore, exposed through `BromeSettings` / `BromeAppearance`, and survive process death and restarts.

## Audit and Wiring Table

| Setting | Preference Key | Default Value | Status Before | Where Applied Now (After Fix) | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Sender Bubble Color** | `sender_bubble_color` | `null` (Theme Primary) | Read in Settings | Applied in `VariantBChatBubble` & `ChatsSettingsContent` | Updates sent bubble fill or outline color live |
| **Receiver Bubble Color** | `receiver_bubble_color` | `null` (SurfaceContainer) | Read in Settings | Applied in `VariantBChatBubble` & `ChatsSettingsContent` | Updates received bubble fill or outline color live |
| **Bubble Theme** | `brome_bubble_theme` | `TIDE` | Read in Settings | Applied in `VariantBChatBubble` & preview tiles | Changes shape & stroke across Tide, Round, Flat, Outline |
| **Dynamic Color** | `dynamic_color_enabled` | `true` | Working | Root `BromeTheme` in `MainActivity.kt` | Uses Android 12+ wallpaper seed on API 31+ |
| **Custom Seed Color** | `custom_seed_color` | `0xFF2196F3` | Working | Root `BromeTheme` in `MainActivity.kt` | Sets primary/secondary scheme when dynamic color is off |
| **Theme Mode** | `theme_mode` | `SYSTEM` | Working | Root `BromeTheme` in `MainActivity.kt` | Light, Dark, or System mode |
| **Typeface Style** | `brome_typeface_style` | `TIDE` | Read in Settings | Root `BromeTypography` in `Theme.kt` | Applies Tide, Rounded, Classic, Serif, Mono, or System fonts |
| **Text Size Scale** | `brome_text_size_scale` | `DEFAULT` (1.0x) | Read in Settings | Root `LocalDensity` in `MainActivity.kt` | Multiplies user font scale (Small 0.9x, Default 1x, Large 1.15x) |
| **Corner Radius** | `brome_corner_radius` | `30.0f` | Read in Settings | `getTideCornerShape` & `BromeTheme` Material `Shapes` | Scales all card, tile, sheet, and dialog corner roundness (8dp–40dp) |
| **Keep One Sharp Corner** | `brome_keep_one_sharp_corner` | `true` | Read in Settings | `getTideCornerShape` | Controls signature 10dp sharp corner on Tide cards |
| **Background Design** | `brome_background_design` | `SHARDS` | Read in Settings | Root `BromeBackground` in `MainActivity.kt` | Procedurally draws Shards, Blobs, Waves, Shapes, Contours, Dots, Mist, Plain, AMOLED |
| **Background Seed** | `brome_background_seed` | `4821` | Read in Settings | Root `BromeBackground` in `MainActivity.kt` | Shuffle button draws a new random seed arrangement |
| **Gradient Wash Level** | `brome_gradient_wash_level` | `MINOR` | Read in Settings | Root `BromeBackground` in `MainActivity.kt` | Applies vertical top/bottom gradient wash (Off, Minor, Soft) |
| **Navigation Pill Height** | `brome_nav_pill_height` | `DEFAULT` (72dp) | Read in Settings | `TideFloatingNavAndFab` & bottom list padding | Scales bottom floating navigation bar (Compact 64dp, Default 72dp, Large 80dp) |
| **Compose Button Size** | `brome_fab_size` | `DEFAULT` (72dp) | Read in Settings | `TideFloatingNavAndFab` & compose FAB | Scales compose FAB (Small 56dp, Default 72dp, Large 88dp) |
| **Search Bar Default Text** | `brome_search_hint_text` | `"Search messages"` | Read in Settings | `TideTopBar` search pill across Inbox, Contacts, Starred, Archive | Sets hint text inside search pills |
| **Enable Categorization** | `enable_categorization` | `true` | Working | `ChatsViewModel` & `ChatsScreen` | Enables or disables smart category sorting |
| **Active Categories** | `enabled_categories` | 8 Categories | Working | `TideCategoryGrid` & `ChatsScreen` | Toggles category chips and inbox filters |
| **Category Custom Colors** | `category_colors` | Category Defaults | Working | `BromeCategoryColors` & tag pills | Custom color overrides per category |
| **OTP Auto-Delete** | `brome_otp_auto_delete` | `HOURS_24` (24 hours) | Read in Settings | `AutoDeleteWorker` & `ChatsViewModel` | Enqueues WorkManager periodic cleanup job |
| **Spam Auto-Delete** | `brome_spam_auto_delete` | `DAYS_7` (7 days) | Read in Settings | `AutoDeleteWorker` & `ChatsViewModel` | Enqueues WorkManager periodic cleanup job |
| **Recycle Bin Retention** | `brome_trash_auto_delete` | `DAYS_30` (30 days) | Read in Settings | `AutoDeleteWorker` & `TrashManager` | Automatically purges deleted messages older than retention period |
| **Language** | `brome_language_code` | `"system"` | Read in Settings | `AppCompatDelegate.setApplicationLocales` & `MainActivity` | Changes app locale and script live without restart |

All settings are verified **Working** with live updates and DataStore persistence across restarts.
