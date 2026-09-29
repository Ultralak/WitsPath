# WitsPath website -> Android migration

The Android Home screen has been migrated to match the supplied WitsPath website design while remaining native Android UI.

## Included
- Website-style WitsPath header and navigation bar.
- Responsive phone/portrait layout with stacked route panel and map.
- Tablet and landscape layout with a desktop-style route panel beside the map.
- Existing Android `campusmap.jpeg` drawable reused for the campus map.
- Existing Android icons reused where appropriate, with native vector drawables added only for website-specific icons that did not already exist.
- Website four-mode route selector:
  - Wheelchair Accessible
  - Walking Aid
  - Visual Assistance
  - General Route
- Route mode selection now changes the native A* routing behavior and uses `accessibilityCost` as a distance multiplier, matching the website routing model.
- Step-free behavior for wheelchair/visual modes, walking-aid stair penalty, lift preference and steep-ramp preference wiring.
- Native route summary, step-by-step guidance, route markers and map controls.
- Website-style dark palette support through Android night resources.
- Translator migration expanded to the website's language set: English, Afrikaans, isiNdebele, Sepedi, Sesotho, Setswana, siSwati, Tshivenda, Xitsonga, isiXhosa and isiZulu.
- Website translations were reused for the migrated Android UI strings where corresponding translations exist.

## Build note
The supplied project uses a Gradle 9.6 wrapper. The source was XML-validated and statically checked in this environment, but a full Gradle compilation could not be completed because the Gradle distribution was not available locally and external network access was unavailable.

The original Windows `gradlew`/`gradlew.bat` files are preserved for normal Android Studio/Windows builds.
