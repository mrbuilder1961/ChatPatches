# Changelog

## Chat Patches `194.5.1` for Minecraft 1.19.3, 1.19.4 on Fabric, Quilt
- Fixed a couple README formatting issues
- Changed the default config value for `counterFormat` to use '&r' so `counterColor` will appear to apply right out-of-the-box
- Updated the changelog snippet function in `build.gradle` that automatically populates new version descriptions when publishing
*If you noticed the larger file size, don't worry, most of it is just images. These will be compressed further in the future, when the YACL image-crashing bug is fixed.

## Chat Patches `194.5.0` for Minecraft 1.19.3, 1.19.4 on Fabric, Quilt
### Note: Based on popular opinion, Yet Another Config Lib and Mod Menu are now required.
- Changed the "Chat HUD" config category to "Chat Interface" again, and split the options within it into 2 subcategories: "Heads Up Display (HUD)" and 
  "Screen" for more organization
- You can now use vanilla chat clearing (`vanillaClearing`) if you really want.. ([#85](https://www.github.com/mrbuilder1961/ChatPatches/issues/85))
- Fixed the up arrow switching focus to the search field while also accessing sent message history? ([#86](https://www.github.com/mrbuilder1961/ChatPatches/issues/86))
- Completely revamped the method to copy messages! Instead of the old and clunky `/copymessage` command, you just right-click on a message and a menu will 
  appear with a multitude of options to access data contained within the message. For example, you can copy the raw message, a json representation, or 
  even any links within the message! ([#77](https://www.github.com/mrbuilder1961/ChatPatches/issues/77), [#87](https://www.github.com/mrbuilder1961/ChatPatches/issues/87))
- There are two config options associated with the new copy menu: `copyColor` and `copyReplyFormat` which control the color of the selection box around the 
  clicked message, and the text that is put into the chat box when clicking on "Reply", respectively
- All messages now store their time received in the timestamp's insertion text, which just means if you shift-left click on the timestamp text, it will insert 
  the unix timestamp of the received message into the chat box
- Removed the `/copymessage` command and its translations
- Added `fr_fr` translations (huge thanks to [Calvineries!](https://www.github.com/Calvineries))
- Refactored some config options and their translations (`saveChat` => `chatLog`, `nameFormat` => `chatNameFormat`, and `maxMsgs` => `chatMaxMessages`) to 
  work properly with the new subgroups
- Fixed the dupe message adder method breaking when a boundary line was the last message sent
- You can now choose to use a [CompactChat](https://www.modrinth.com/mod/compact-chat) style dupe counter, which is explained in the config ([#67](https://www.github.com/mrbuilder1961/ChatPatches/issues/67))
- There is now a CompactChat subgroup under the dupe counter category in the config, which provides `counterCompact` for the toggle and 
  `counterCompactDistance` for how many messages to check for duplicates
- Updated YetAnotherConfigLib to 3.0.3-fabric, which overhauls the config UI to use a new tab system, but more notably has image previews now! For 
  this reason along with popular opinion, YACL is now required to load Chat Patches ([#91](https://www.github.com/mrbuilder1961/ChatPatches/issues/91))
  - ***WARNING: The images currently crash the game, so they've been temporarily disabled until a bugfix is released. Check back in a week or so for the 
    hotfix!***

Misc changes and developer stuff:
- Added some more detailed JavaDocs overall
- The old method that was used to condense duplicate messages was overhauled with a slightly faster implementation that doubles as a way to use the 
  CompactChat style dupe counter. The original method was moved to `ChatUtils#getCondensedMessage(Text, int)` and instead removes the old message, rather 
  than updating the old message and ignoring the new one
- Also cleaned up the `YACLConfig` class a little more to use less "magic numbers" and more clear method calls
- Added the `[Config.writeCopy]` prefix to a logger call in `Config#writeCopy()` to maintain consistency and clarity
- Replaced the common obtaining of `ChatHudAccessor` from the ugly `(ChatHudAccessor) client.inGameHud.getChatHud()` with two static methods, 
  `ChatHudAccessor.from(ChatHud)` and `ChatHudAccessor.from(MinecraftClient)`, which wrap the cast for convenience
- Added message indices stored in `ChatUtils` to clearly identify what data is being interacted with, instead of magic numbers everywhere
- Added the `RenderUtils` class, which currently only has a subclass `MousePos`
- Added `StringTextUtils#getLinks(String)` for getting all links from a string, used in the copy menu
- All of `ChatScreenMixin` is really just complete hell, it's all over the place, but most of the complex methods have javadocs (except for 
  `#cps$loadCopyMenu(mX, mY)` which has comments inside). It works especially now together with `MenuButtonWidget` which is also disgusting. In the future, 
  when I have time, I will rewrite the whole menu thing as a new class just for menus and buttons, and then use that. For now, I'm so sorry.

## `194.4.6` for Minecraft 1.19.3, 1.19.4 on Fabric, Quilt
- Removed the floating gray search background when `hideSearchButton` is enabled ([#83](https://www.github.com/mrbuilder1961/ChatPatches/issues/83))
- Also added the mixin prefix `cps$` to a few unique methods in `ChatScreenMixin` that previously were unmarked

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

## (TEMPLATE) Chat Patches `{version}` for Minecraft {targets} on {loaders}
- Changes go here

Typically as a list but not required!
