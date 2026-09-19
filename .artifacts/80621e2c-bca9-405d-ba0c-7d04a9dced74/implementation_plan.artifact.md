# WitsPath UI/UX Redesign Implementation Plan

This plan outlines the complete visual identity transformation of the WitsPath application, shifting from a dark-themed interface to a professional, Wits-inspired white and blue design system with a dynamic language-specific accent system.

## User Review Required

> [!IMPORTANT]
> **Base Theme Flip**: The application will transition from a dark background to a white/light background. This is a significant change in visual density and contrast.
> **Language Accents**: Every language will have its own vibrant accent palette (e.g., isiZulu -> Violet, isiXhosa -> Cyan). These will be used for interactive elements and highlights.
> **Strings Protection**: No changes will be made to `strings.xml`. All content remains authoritative.

## Proposed Changes

### 1. Design System Foundation (Core Styling)

We will redefine the color palette and typography system to establish the Wits-inspired identity.

#### [MODIFY] [colors.xml](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/res/values/colors.xml)
- Flip base tokens:
    - `colorInk` (Background) -> White (`#FFFFFF`) or Near-white (`#F8F9FA`).
    - `colorInkText` (Primary Text) -> Dark Wits Blue (`#002C5F`).
    - `colorInkTextMuted` (Secondary Text) -> Medium Grey-Blue (`#556B8D`).
    - `colorPlate` (Surface) -> Very Light Blue-Grey (`#F1F4F8`).
    - `colorRoute` (Primary Action) -> Wits Blue (`#003B7E`).
- Define Language Accent Tokens (Primary, Dark, Light, Surface, Border) for each supported language.

#### [NEW] [Locale-Qualified Colors](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/res/values-en/colors.xml)
- Create `values-en/colors.xml`, `values-zu/colors.xml`, etc.
- Map the dynamic `colorAccent` tokens to the respective language colors defined in the base `colors.xml`.

#### [MODIFY] [themes.xml](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/res/values/themes.xml)
- Update `Base.Theme.WitsPath` to reflect the light-mode foundation.
- Wire in the new typography tokens.
- Ensure `colorAccent` refers to the locale-qualified resource.

#### [MODIFY] [styles.xml](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/res/values/styles.xml)
- Redesign shared components: `WitsPath.Plate`, `WitsPath.SettingRow`, `WitsPath.TextField`, etc.
- Implement responsive spacing and improved visual hierarchy (cards, shadows, rounded corners).

---

### 2. Responsive Typography System

Wiring the existing `Prefs.KEY_TEXT_SIZE` into a robust typography pipeline.

#### [MODIFY] [AppConfiguration.java](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/java/com/example/witspath/util/AppConfiguration.java)
- Refine the `fontScale` mapping.
- Ensure `sp` values in layouts are correctly interpreted by the scaling logic.

---

### 3. Screen Redesigns

Systematic update of all application screens to the new identity.

#### [MODIFY] [activity_home.xml](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/res/layout/activity_home.xml)
- Modernise the map container and search overlays.
- Apply Wits Blue branding and language accents to active path indicators.

#### [MODIFY] [activity_settings.xml](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/res/layout/activity_settings.xml)
- Redesign section grouping and row styling.
- Improve accessibility of switches and chip groups.

#### [MODIFY] [RoomPickerActivity.java](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/java/com/example/witspath/ui/RoomPickerActivity.java)
- **Approved Change**: Replace programmatic `TextView` creation with `LayoutInflater.inflate(R.layout.item_room, ...)` to ensure rows match the design system.

#### [MODIFY] [nav_drawer_content.xml](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/res/layout/nav_drawer_content.xml)
- Redesign the navigation header and menu items with Wits-inspired aesthetics.

---

### 4. Language & Assets

#### [MODIFY] [Languages.java](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/java/com/example/witspath/util/Languages.java)
- Update color constants to match the modernised palette.
- Ensure the `apply` method continues to trigger global refreshes correctly.

#### [MODIFY] [Drawables](file:///C:/Users/3012856/StudioProjects/WitsPath/app/src/main/res/drawable/)
- Update icons and background shapes (rounded corners, subtle gradients).

## Verification Plan

### Automated Tests
- Build and Run: `gradlew assembleDebug`
- Verification of resource resolution across locales.

### Manual Verification
- **Visual Identity Check**: Verify White/Blue foundation on Home, Login, and Settings.
- **Language Accent Check**: Switch between English (Blue), isiZulu (Violet), isiXhosa (Cyan), etc., and verify accent changes in navigation and buttons.
- **Text Scaling Check**: Set text size to `Huge` and verify no clipping or overlapping in `SettingsActivity` and `RoomPickerActivity`.
- **Functionality Regression**:
    - Guest Login / Sign Up works.
    - Pathfinding and routing remain accurate.
    - Room search and selection work in `RoomPickerActivity`.
    - All preferences persist correctly.
