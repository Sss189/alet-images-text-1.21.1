# Attribution

This is an unofficial Minecraft 1.21.1 adaptation of the A Little Extra Tiles
Photo Importer and Typewriter functionality by Docvin (_Doc / Doc-1).

Upstream: https://github.com/Doc-1/A-Little-Extra-Tiles
Reference master snapshot: e20e2cf299851952cf69a3cc4136621021561a1f.
Reference rewrite snapshot (not the implementation base): 69ce0c84d50977235204eb33fe86fae5bbd716bd.

The new implementation preserves the image-to-colored-tiles and font-to-image
workflow, and uses modern LT SNBT and blueprint data components. It does not
include the old Forge GUIs, programmable structures, circuits, ropes or music.
No upstream font, sound, texture asset or GifDecoder is included in the JAR.

The original CurseForge project declares GPLv3; Modrinth declares LGPL-3.0-only.
This adaptation is distributed under GPL-3.0-only, including complete source.
The MIT license in the upstream 1.12.2 rewrite belongs to the CleanroomMC
development template; it is not used to relicense the original ALET project.

LittleTiles and CreativeCore are external dependencies and are not bundled.
Minecraft and NeoForge are provided by the user's installation.
