# Changelog

## `194.4.5` for Minecraft 1.19.3, 1.19.4 on Fabric, Quilt
- Showing/hiding the chat search bar or the chat search settings will now persist after closing and reopening chat ([#81](https://www.github.com/mrbuilder1961/ChatPatches/issues/81))
- You can now additionally hide the search button entirely, but keep in mind this disables all other chat searching functionality

## `194.4.4` for Minecraft 1.19.3, 1.19.4 on Fabric, Quilt
- Updated dependencies, including YACL to `2.5.0-fabric` so you may need to update that as well
- Fixed [#78](https://www.github.com/mrbuilder1961/ChatPatches/issues/78), which uses a large mixin instead of an entirely new screen to allow for compatibility with other mods
- Removed the `chatSearchScreen` option because the search settings can be hidden and other mods should be compatible now
  - That being said, the only real difference is that the search settings button cannot be removed from the chat screen
- Added the `messageDrafting` and `searchDrafting` options ([#79](https://www.github.com/mrbuilder1961/ChatPatches/issues/79)), which when toggled let any text in the fields persist after closing and reopening the chat
- Tweaked the README config table to add double quotes around String options and removed the decimal numbers attached to Color options

- Updated the changelog from only containing the latest version's changes to all of them starting from `194.4.0`. Unless you're a contributor, this doesn't 
  really matter
- Contributor note: Now when you edit the changelog, assuming you run `build` or `publish` (which both subsequently run `processResources`) before 
  committing any changes, this file will 
  - have any double hashtags (`##`) followed by a number replaced with a GitHub issue link
  - replace any instances of `${}` with either `version`, `targets`, or `loaders` in between the brackets with the appropriate value from the 
    `gradle.properties` file
- You can now specify the `-PnoPublish=true` flag when running `./gradlew publish` to prevent the file from actually being published on Modrinth and 
  Curseforge. This helps to avoid needing to add `debug=true` every time you want to double-check the changelog before publishing

## `194.4.3` for Minecraft 1.19.3, 1.19.4 on Fabric, Quilt
- Fixed [#73](https://www.github.com/mrbuilder1961/ChatPatches/issues/73), which was caused by a slight method change from `1.19.3` to `.4`. To fix this I simply removed it, which was fine
  because it didn't really work in the first place.
- However, the method that was tweaked to fix this issue prevented a couple problems relating to selecting both the search and chat fields at the same time
  (but it can't anymore)
- So to try and help with any issues that may arise from this, whenever you type a chat message that hasn't been sent yet, if you close the chat and reopen
  it the message will reappear in the chat field.

## `194.4.2` for Minecraft 1.19.3, 1.19.4 on Fabric, Quilt
- Fixed a couple of grammar mistakes

## `194.4.1` for Minecraft 1.19.3, 1.19.4 on Fabric, Quilt
- Fixed [#72](https://www.github.com/mrbuilder1961/ChatPatches/issues/72) (pressing the slash key doesn't put the slash in the input box)

## `194.4.0` for Minecraft 1.19.3, 1.19.4 on Fabric, Quilt
- Updated to FAPI 0.77.0 and YACL 2.4.0
- Added the `regex` and `regex_tester` links in the YACL Help category for web links to regex assistance.
- Added the new ChatSearchScreen screen, an extension of ChatScreen that allows for searching through chat messages plus more features!
- Added the `chatSearchScreen` option that toggles the new chat search screen (which overrides the vanilla chat screen)
- Added a new mixin injector to override the vanilla chat screen with the new one (if enabled)
- Added some new String util methods
- Fixed the '&<?>'-formatted string to text parser not removing double backslashes from literal uses of ampersands
- Added a button icon and a background sprite

This update officially fixes [#4](https://www.github.com/mrbuilder1961/ChatPatches/issues/4)!!! sorry for taking so long, but hey it's done now!

## (TEMPLATE) `{version}` for Minecraft {targets} on {loaders}
- Changes go here

Typically as a list but not required!