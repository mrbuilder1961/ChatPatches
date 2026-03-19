# Changelog

## Chat Patches `8.0-alpha.8` for Minecraft 1.20.1–26.1 on Fabric, Quilt
- Updated to 26.1! Like always with recent Minecraft drops, A LOT has changed under the hood, so be on the lookout for bugs!
- Requires Fabric Loader 0.18.0+ going forward
- Added *another* Band-Aid patch for [#252](https://www.github.com/mrbuilder1961/ChatPatches/issues/252), again thanks to [SkyNotTheLimit](https://discord.com/channels/507304429255393322/1452165613756879044/1452516265330475085)!
- Added the Delete button to the context menu! When clicked it displays a warning forcing you to confirm or cancel the operation, because message deletion 
  cannot be undone. The burn item sound will play when a message is successfully deleted as an extra indicator something happened. **To be clear this is 
  purely client side**, _nothing is actually deleted_ from the server chat
- Fixed non-vanilla messages formatted like vanilla ones having an extra space between the name and message ([#233](https://www.github.com/mrbuilder1961/ChatPatches/issues/233))
- Mostly fixed Chat Heads' `BEFORE_NAME` setting not working with Chat Patches on 1.21.9+, thanks mostly to
  [Fourmisain](https://github.com/Fourmisain)! ([#285](https://www.github.com/mrbuilder1961/ChatPatches/issues/285) and [#297](https://www.github.com/mrbuilder1961/ChatPatches/issues/297))
- Fixed a 1.21.10 crash from right-clicking to open the context menu ([#298](https://www.github.com/mrbuilder1961/ChatPatches/issues/298))
- Error and info toasts are now pushed on the render thread ([#302](https://www.github.com/mrbuilder1961/ChatPatches/issues/302))
- Fixed the config not writing default values to disk
- Optimized the dynamic shift calculator - it no longer runs every render tick!

## Chat Patches `8.0-alpha.7` for Minecraft 1.20.1–1.21.11 on Fabric, Quilt
- Updated to 1.21.11 (blame Mojang for the long wait - they redesigned the chat again 🤬) ([#293](https://www.github.com/mrbuilder1961/ChatPatches/issues/293))
- Removed optional dependency for Placeholder API/QuickText - it doesn't support the main feature I thought it did :(
- Removed the annoying "Chat log not available" warning as it didn't fix what it was supposed to ([#263](https://www.github.com/mrbuilder1961/ChatPatches/issues/263) remains at large...)
- Introduced what seems to be at least a temporary fix for [#252](https://www.github.com/mrbuilder1961/ChatPatches/issues/252)/[#180](https://www.github.com/mrbuilder1961/ChatPatches/issues/180), 
  all thanks to [SkyNotTheLimit](https://discord.com/channels/507304429255393322/1452165613756879044/1452516265330475085)! As always, please make sure to report any bugs you experience on the Github
- Marked [MoreChatHistory](https://modrinth.com/mod/morechathistory) as incompatible - it won't crash your game, but it's useless with Chat Patches
- **Dev notes:**
  - Chat Patches now uses an access widener to limit unnecessary code overhead - definitely not because I finally realized they're cool and useful (although 
    thanks to modstitch they automatically transpile to access transformers when needed!)
  - Refactored a ton of nitpicky stuff, most notably all util classes, which are now singular!
  - Unfortunately, Mojang hates me (mod developers) personally, and multiple critical methods were removed - so I introduced `VersionUtil` for the random 
    methods that have been removed or altered across versions to keep them constant between Stonecutter builds

## Chat Patches `8.0-alpha.6` for Minecraft 1.20.1–1.21.10 on Fabric, Quilt
- Fixed player heads not rendering in the context menu on 1.21.9+
- Fixed the command key not placing a slash in the chat input box on 1.21.9+ ([#270](https://www.github.com/mrbuilder1961/ChatPatches/issues/270))
- Fixed chat drafts not always saving if both `messageDrafting` and `saveChatDrafts` were enabled
- Fixed `onlyInvasiveDrafting` not working when `messageDrafting` was disabled
- Fixed a crash that could occur when clicking on a chat message with both a website and file link
- Fixed (again) a crash that could occur when clicking on the JSON copy button if the message contained a non-serializable click event
- Fixed some links not being detected in the context menu
- Fixed right-clicking outside the chat area deselecting the chat input box ([#279](https://www.github.com/mrbuilder1961/ChatPatches/issues/279))
- Updated Gradle to 9.1.0 and fixed a subsequent `build.gradle.kts` issue
- Removed `compactDistance`'s special option for `-1`, which previously made the dupe counter check the entire message list: this is extremely inefficient 
  and not practical
- Clarify some config option buttons
- Fixed the playername color option not persisting on some older Minecraft versions ([#265](https://www.github.com/mrbuilder1961/ChatPatches/issues/265))
- Overhauled how the boundary line is added to the chat, fixing some issues with it showing up redundantly

## Chat Patches `8.0-alpha.5` for Minecraft 1.20.1-1.21.10 on Fabric, Quilt
- Chat log dumps can now be loaded in-game by renaming them to `chatlog.json`. Note that this does not perfectly restore the original log, as most style 
  data is lost
- The chat input box will now be grayed out while the chat log is loading to prevent sent messages being moved to the end of chat history
- Now compatible with 1.21.9–10! ([#269](https://www.github.com/mrbuilder1961/ChatPatches/issues/269))
  - Going forward, the `onlyInvasiveDrafting` option will be ignored and reflected as such in the config screen, as it's been implemented in vanilla
- Doesn't show the 'Hover Text' button in the context menu if there is none to show
- Now requires [Placeholder API](https://modrinth.com/mod/placeholder-api). Mod Menu also requires it since 9.2.0 (1.20.4) so this shouldn't be an issue; but <1.20.4 or on non-ModMenu installs, 
  you will need to install it manually
- **Dev notes:**
  - For IntelliJ users or anyone using an IDE with import optimization, YOU NOW MUST DISABLE IT BEFORE PROPOSING CHANGES. Stonecutter currently doesn't have 
    a working fix, and typing the entire qualifier EVERYWHERE (especially for swaps) is simply unreadable. You may manually trigger optimizing **IF** you 
    **ensure** all is well with the stonecutter directives *BEFORE* pushing changes, but the automatic system WILL DELETE THEM!!
  - Updated the chat log to restore messages explicitly on the render thread, due to Minecraft's begging (crashing) over it - expect more issues to stem 
    from this

## Chat Patches `8.0-alpha.4` for Minecraft 1.20.1-1.21.8 on Fabric, Quilt
- ACTUALLY fixed 'Disallowed character' toast and log spam ([#246](https://www.github.com/mrbuilder1961/ChatPatches/issues/246))
- Now requires Fabric Loader `0.17.0`+ (doesn't currently apply, but will also require NeoForge `21.1.18`+)
- Override chat height now explicitly changes the **focused** chat height, and the docs + config suggest this ([#254](https://www.github.com/mrbuilder1961/ChatPatches/issues/254))
- Chat log now instead dumps the current chat to `chatlog_dump_<time>.json` instead of to the log. ~~This can be renamed to `chatlog.json` to restore the log 
  if needed~~ Doesn't work yet, but it will soon
- Removed log spam from messages being formatted incorrectly ("`DUPE_INDEX` is out of bounds")
- Updated `zh_cn` translations thanks to [layou233](https://github.com/layou233)! ([#255](https://www.github.com/mrbuilder1961/ChatPatches/pull/255))
- Finally marked ModMenu and Menulogue + Catalogue as under `suggests` instead of `recommends` according to Fabric. This should make log files a tad less 
  confusing for some users, but to be clear, these are still **HIGHLY RECOMMENDED** so you can actually use the config in-game!

## Chat Patches `8.0-alpha.3` for Minecraft 1.20.1-1.21.8 on Fabric, Quilt
- Fixed the main crash from [#250](https://www.github.com/mrbuilder1961/ChatPatches/issues/250)
- Fixed the search bar accidentally including invisible formatting codes in the search results
- Fixed the search bar not initially working with certain settings
- Fixed the context menu using incorrect messages when searching

## Chat Patches `8.0-alpha.2` for Minecraft 1.20.1-1.21.8 on Fabric, Quilt
### Since alpha.1:
- [#243](https://www.github.com/mrbuilder1961/ChatPatches/issues/243)
- ~~Fixed 'Disallowed character' spam ([#246](https://www.github.com/mrbuilder1961/ChatPatches/issues/246))~~
- Java 17 compat ([#247](https://www.github.com/mrbuilder1961/ChatPatches/issues/247))

### Since x.6.16:
- Completely refactored the mod using Stonecutter and Modstitch! ([#137](https://www.github.com/mrbuilder1961/ChatPatches/issues/137))
- Replaced the copy menu with the Context Menu! New features include a toggle, main buttons copying their first 
  sub-button, and new parts to copy! ([#128](https://www.github.com/mrbuilder1961/ChatPatches/issues/128), [#129](https://www.github.com/mrbuilder1961/ChatPatches/issues/129), [#166](https://www.github.com/mrbuilder1961/ChatPatches/issues/166), [#168](https://www.github.com/mrbuilder1961/ChatPatches/issues/168), [#185](https://www.github.com/mrbuilder1961/ChatPatches/issues/185))
- Renamed many config options; **for now** you will need to **manually rename** these options:
  - `counterCompact` => `compactChat`
  - `counterCompactDistance` => `compactDistance`
  - `shiftChat` => `chatShift`
  - `chatName` => `name`
  - `chatNameFormat` => `nameFormat`
  - `chatNameColor` => `nameColor`
  - `contextColor` => `contextOutlineColor`
  - `hideSearchButton` => `search` (invert old value)
  - `timeSystemMessages` ([#162](https://www.github.com/mrbuilder1961/ChatPatches/issues/162))
  - `formatting` => (removed: broken and unused)
- The config file will now only save non-default options
- All I/O events except for config loading are now run on dedicated threads ([#227](https://www.github.com/mrbuilder1961/ChatPatches/issues/227))
- Fatal chat log errors will now try to dump saved data to the log
- Fixed team names and related components not formatting
- Error toasts will now show when the chat log breaks, ensuring quicker fixes
- and ***SO*** much more!

## Chat Patches `8.0-alpha.1` for Minecraft 1.20.1-1.21.7 on Fabric, Quilt
- STONECUTTER AHHHHHHHHHHH
- everything happened. ignore this changelog tho it's a placeholder

## Chat Patches `202.6.5` for Minecraft 1.20.2 on Fabric, Quilt
- Added the `searchPrefix` config option, which controls whether to filter previously sent messages based on the text behind the cursor, to speed up 
  accessing specific history messages. Thanks [LucunJi](https://github.com/LucunJi) for the code implementing this! ([#152](https://www.github.com/mrbuilder1961/ChatPatches/issues/152), [#154](https://www.github.com/mrbuilder1961/ChatPatches/pull/153))
- Added a help section to the README, which currently explains how to upload log files and access beta versions fresh from Actions
- The chat log will now back up a copy of itself if, upon loading, an error occurs
- **Dev notes:**
  - Redeclared the `MinecraftClient` field in `ChatScreenMixin` to remove false `NullPointerException` warnings

## Chat Patches `202.6.4` for Minecraft 1.20.2 on Fabric, Quilt
- Fixed errors with regex input on the search screen not updating the text color
- Made ChatSearchSettings save when the chat screen is closed then reopened; resets on game restart
- Fixed team name colors, prefixes, and suffixes being ignored when `chatNameFormat` is customized ([#115](https://www.github.com/mrbuilder1961/ChatPatches/issues/115))
- Added a new runnable config option to reload the config from disk
- Added a minor optimization to the way messages are modified to largely simplify the process in a few scenarios [prepub impl]
- Switched the text in the search settings screen to use pre-bundled translations (ON/OFF instead of a visual switch)
- Fixed the Copy String \> Copy Raw String button in the copy menu removing &\<code\> formattings
- **Dev notes:**
  - Changed the `CONFIG_PATH` and `CHATLOG_PATH` variables to use the `Path#resolve(String)` method instead of concatenating strings
  - Removed some (now) redundant file constants and references (in `StringTextUtils` and `Config`)
  - Condensed most `make`... methods in `Config` to be less repetitive
  - `formatPlayername(GameProfile)` now accounts for team metadata as mentioned in the #115 fix
  - Capitalized some static final variables
  - Changed some stuff about how the config is initialized, read, and written to disk
  - Refactor StringTextUtils to TextUtils
  - Restructured the powerhouse `ChatHudMixin#modifyMessage(Text, boolean)` method to be more modular with message reconstruction
  - Moved the bulk of the `modifyMessage` method to ChatUtils to help development and greatly ease future troubleshooting
  - Created a new `ChatUtils#getArg(..)` method to avoid the elusive `ClassCastException`s that kept getting thrown
  - Tweaked the `MessageHandlerMixin#cacheGameData` method to use built-in methods instead of rewriting the same thing
  - Removed the `VANILLA_MESSAGE` matcher in `ChatUtils` because it was redundant

## Chat Patches `202.6.3` for Minecraft 1.20.2 on Fabric, Quilt
- Should be compatible with Quilt again! (requires Loader 0.23.0+)
  - **Note that Chat Patches still isn't developed directly for Quilt, issues may still arise**
- Added the `counterCheckStyle` option, which controls checking style data when condensing duplicate messages ([#144](https://www.github.com/mrbuilder1961/ChatPatches/issues/144))
- Added the `chatNameColor` option, which overrides the color of playernames in vanilla chat messages. ([#133](https://www.github.com/mrbuilder1961/ChatPatches/issues/145))
- Removed MoreChatHistory as incompatible, due to MixinExtra's compatibility injectors! (it's actually been compatible for a while, but I forgot to remove it)
- **Dev info:**
  - Overhauled the publishing system for CurseForge and Modrinth, revived the GitHub Releases channel, and added a webhook to the Discord server to
    automatically post new releases! Now only uses [modmuss50-publish-plugin](https://github.com/modmuss50/mod-publish-plugin), which is much cleaner and easier to use
  - Moved most volatile Gradle variables to `gradle.properties` to make it easier to change them
  - "Synced" some parts of the `build.gradle` file with the fabric-example-mod template to fix old warnings and keep it up to date
  - Added two specific methods to `StringTextUtils` for use with dupe counter methods, to work with the new `counterCheckStyle` option
  - Removed the weird returning message reconstruction segment from `ChatUtils#getCondensedMessage(Text, int)` because it was actually unnecessary!
  - Added some uncertain notes about certain aspects of the dupe counter process

## Chat Patches `202.6.2` for Minecraft 1.20.2 on Fabric
### - Skipped version `202.6.1` to re-sync with the 1.20.4 version
- Remove Gradle integration system, it was completely unnecessary and unused
- Fixed messages starting with a newline not being copyable
- Switched `chatWidth`'s YACL max value to the game window's width. Note: this can be incorrect if the game window is resized after the config screen is 
  opened. To fix this, just open the config screen when the game window is at the desired size.
- Fixed a couple issues with the [Smooth Scroll](https://modrinth.com/mod/smooth-scroll) mod thanks to [@SmajloSlovakian](https://github.com/SmajloSlovakian)! 
  ([#139](https://www.github.com/mrbuilder1961/ChatPatches/issues/139), [#141](https://www.github.com/mrbuilder1961/ChatPatches/pull/141))
- **Dev stuff**:
  - Change source Java version to 21 and update Gradle to 8.5
  - Replaced old `Instant` references with `Date` ones for consistency
  - Removed the `SharedVariables` class and redistributed its one useful variable to `ChatPatches`
  - Removed the `MiscUtils` class and redistributed `#currentWorldName(MinecraftClient)` to `ChatPatches`
  - Replaced weird `ChatLog#serialize(boolean, String)` method with `#backup()`
  - Updated `Config#writeCopy()` to use the same thought process as `ChatLog#backup()`

## Chat Patches `202.6.0` for Minecraft 1.20.2 on Fabric
- Removed MixinExtras from dependencies because it's automatically bundled with the Fabric Loader since `0.15.0`!
  - **/!\\ WARNING: NOT CURRENTLY SUPPORTED ON QUILT DUE TO THIS! /!\\**
- Add "Only Invasive Drafting" option ([#131](https://www.github.com/mrbuilder1961/ChatPatches/pull/131) -JustAlittleWolf)
- Improve mod compatibility with the dupe counter injector + teeny optimizations
 ([#132](https://www.github.com/mrbuilder1961/ChatPatches/pull/132) -JustAlittleWolf)
- Extracted `chatLog` to the new ChatLog tab in the config
- Added a new config option to the tab: `chatLogSaveInterval`, which configures the timer for saving the chat log to disk
- Added new executable buttons to the chat log tab to manipulate it in game: Clear all messages, Clear sent messages, Clear regular messages, Load, Save, 
  and Backup
- Additionally, the chatlog will now only actually write to disk if it has been modified since the last save ([#134](https://www.github.com/mrbuilder1961/ChatPatches/issues/134))
- Inform users that Catalogue + Menulogue works as a substitution for ModMenu ([#42](https://www.github.com/mrbuilder1961/ChatPatches/issues/42))
- Removed an unused variable from `ChatSearchSetting`
- Fixed some weird messages and game events formatting incorrectly, such as the Open-to-LAN message

## Chat Patches `202.5.7` for Minecraft 1.20.2 on Fabric, Quilt
- The ChatLog now removes the oldest message and adds the newest one once the max limit is reached ([#130](https://www.github.com/mrbuilder1961/ChatPatches/issues/130))
- Additionally, the ChatLog now only keeps and loads the newest data when loading a chat log file that is over the limit
- Cleaned up other unnecessary code in `ChatLog`

## Chat Patches `202.5.6` for Minecraft 1.20.2 on Fabric, Quilt
- Improve performance when using compact chat ([#118](https://www.github.com/mrbuilder1961/ChatPatches/pull/118) -JustALittleWolf)
- Fix longer messages sometimes not compacting properly ([#123](https://www.github.com/mrbuilder1961/ChatPatches/pull/123) -JustALittleWolf)
- Make ModMenu a recommended dependency instead of a required one ([#120](https://www.github.com/mrbuilder1961/ChatPatches/issues/120))
- Extract textures to individual `.png`s to align with Minecraft's new sprite system
- Removed an unused button sprite
- No longer writes the `chatlog.json` file to disk if the ChatLog is empty
- Fixed the Copy Raw String button including formatting data ([#122](https://www.github.com/mrbuilder1961/ChatPatches/issues/122))

## Chat Patches `201.5.5` for Minecraft 1.20, 1.20.1 on Fabric, Quilt
- YACL images now load without crashing the game! Enjoy preview images right from the get-go!
- Fixed [#108](https://www.github.com/mrbuilder1961/ChatPatches/issues/108), thanks [replaceitem](https://github.com/replaceitem)!
- Fixed some minor grammar issues in the `fabric.mod.json` file
- Reverted the `client` split sources change from `201.5.4` because it was causing issues and is unnecessary
- Fix some mods that inject into `ChatHud.clear()` not working
- Changed `ChatHudAccessor` prefixes

## Chat Patches `201.5.4` for Minecraft 1.20, 1.20.1 on Fabric, Quilt
- Fixed right-clicking on chat messages not showing the copy menu (fixed [#106](https://www.github.com/mrbuilder1961/ChatPatches/issues/106), closed [#107](https://www.github.com/mrbuilder1961/ChatPatches/issues/107))
- Added `ja_jp` translations (huge thanks to [co-91](https://github.com/co-91)!)
- Fixed "Copy Raw String" rarely formatting incorrectly
- Switched the internal main directory from `main` to `client` to match the expected split sources standard

## Chat Patches `201.5.3` for Minecraft 1.20, 1.20.1 on Fabric, Quilt
- Better [#86](https://www.github.com/mrbuilder1961/ChatPatches/issues/86) fix, also with auto-complete suggestor not going up when pressing the up key ([#101](https://www.github.com/mrbuilder1961/ChatPatches/issues/101))
- Fixed elements added by other mods are not clickable
- Fixed Tweakeroo message draft not working
- Added Ignore Hide Message Packet option toggle (`chatHidePacket`)
- Fixed chat focus bug again, inline with #86 and #101 above
- Re-implemented another part of the overall fix for [#99](https://www.github.com/mrbuilder1961/ChatPatches/issues/99) (checks that `chatNameFormat` was modified before applying it)
- Removed all `cps$` prefixes as mixin will automatically add them
- Swapped the copy menu's Copy Raw String and Copy Formatted String functions. Copy Formatted String now copies the message with <&?> codes, although
  they do sometimes redundantly repeat.
- Fixed a large lag spike that would occur when opening the chat with a lot of messages loaded ([#102](https://www.github.com/mrbuilder1961/ChatPatches/issues/102))
  - ***Note:** This will still happen if you have something in the search field and search drafting is enabled. However, this is **not considered a bug**
    because lag is to be expected when searching through large lists of data.*
- Added a little more documentation

- ## Chat Patches `201.5.2` for Minecraft 1.20, 1.20.1 on Fabric, Quilt
- I guess I lied about the version numbers thing... doesn't always make sense like in this situation
- Fixed invalid mixin signature (not sure why my plugin to avoid this problem wasn't working...)

- ## Chat Patches `201.5.1` for Minecraft 1.20, 1.20.1 on Fabric, Quilt
- Ported `194.5.1` to 1.20 ([#92](https://www.github.com/mrbuilder1961/ChatPatches/issues/92))
- From now on, all mod versions with the same features will use identical version numbers (excluding the Minecraft version part) to
  make it easier to track changes across versions

## Chat Patches `194.5.1` for Minecraft 1.19.4 on Fabric, Quilt
- ***Removed compatibility with `1.19.3` because YetAnotherConfigLib 3.x doesn't support it***
- Added zh_cn.json translations (HUGE thanks to [SJC08](https://github.com/SJC08)!)
- Show Me What You Got now works with Chat Patches again! ([#88](https://www.github.com/mrbuilder1961/ChatPatches/issues/88))
- Now some odd formatting mods/plugins should work with Chat Patches again ([#96](https://www.github.com/mrbuilder1961/ChatPatches/issues/96))
- Fixed a couple README formatting issues
- Changed the default config value for `counterFormat` to use '&r' so `counterColor` will appear to apply right out-of-the-box; same fix applied to
  `boundaryFormat` for `boundaryColor`
- Updated the changelog snippet function in `build.gradle` that automatically populates new version descriptions when publishing
- Added some more loggers in `ChatLog` so if it runs out of space it should tell you... (open any issues if it randomly clears the log please!)
- Switched the preview image file types from .jpg to .webp to reduce file size, and because YACL (is supposed to) support it well
- *Unfortunately YACL still is crashing with the images, so they remain disabled. [I've opened up an issue on the YACL repo](https://github.com/isXander/YetAnotherConfigLib/issues/87),
  so hopefully it will be fixed soon! **Note: this is why the file size of the mod is much higher now.***

## Chat Patches `194.5.0` for Minecraft 1.19.4 on Fabric, Quilt
### Note: Based on popular opinion, Yet Another Config Lib and Mod Menu are now required.
- Changed the "Chat HUD" config category to "Chat Interface" again, and split the options within it into 2 subcategories: "Heads Up Display (HUD)" and
  "Screen" for more organization
- You can now use vanilla chat clearing (`vanillaClearing`) if you really want.. ([#85](https://www.github.com/mrbuilder1961/ChatPatches/issues/85))
- Fixed the up arrow switching focus to the search field while also accessing sent message history? ([#86](https://www.github.com/mrbuilder1961/ChatPatches/issues/86))
- Completely revamped the method to copy messages! Instead of the old and clunky `/copymessage` command, you just right-click on a message and a menu will
  appear with a multitude of options to access data contained within the message. For example, you can copy the raw message, a json representation, or
  even any links within the message! ([#77](https://www.github.com/mrbuilder1961/ChatPatches/issues/77), [#87](https://www.github.com/mrbuilder1961/ChatPatches/issues/87))
- There are two config options associated with the new copy menu: `contextColor` and `contextReplyFormat` which control the color of the selection box around the
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