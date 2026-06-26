# Changelog

## Chat Patches `8.0-alpha.10` for Minecraft 1.20.1, 1.21.1+5, 1.21.8–26.2 on Fabric, Quilt
- Published builds now come with signatures ~~and checksums~~! This means you can verify the integrity of Chat Patches builds (and ensure they haven't been
  tampered with)! See the README for more info! Currently only available on Modrinth.

- The placeholder (`$`) in `timeFormat` is now optional ([#277](https://www.github.com/mrbuilder1961/ChatPatches/issues/277))
- Prevents vanilla's command history from being loaded if the chat log is enabled ([#300](https://www.github.com/mrbuilder1961/ChatPatches/issues/300))
  - Note: I wrote this a while ago but couldn't publish it due to dependency requirements, so lmk if you find any issues!!
- Added an updated `ko_kr` translation thanks to [ettip](https://github.com/ettip) in [#317](https://www.github.com/mrbuilder1961/ChatPatches/issues/317)! (I have no idea why the original translation was removed)
- Increase max compact chat distance to 50 ([#319](https://www.github.com/mrbuilder1961/ChatPatches/issues/319))
- Partially implemented ([#323](https://www.github.com/mrbuilder1961/ChatPatches/pull/323))
- Fixed the chat un-scrolling after deleting a message when `contextDeletionWarning` was enabled
- Added a toggle for the message deletion sizzle sound

- ***Known issues:***
  - Entering an invalid value to a config option will **falsely** log a "Value mismatch! Reset to binding's getter" error
  - Modrinth (and apparently the buildscript) doesn't support uploading checksum files yet, despite the capability existing now

- **Dev notes:**
  - Added Fletching Table as a dependency, which primarily allows for versionable mixins (needed for #300 and the future implementation of
  - [#109](https://www.github.com/mrbuilder1961/ChatPatches/issues/109) on 1.21.11+)

## Chat Patches `8.0-alpha.9` for Minecraft 1.20.1, 1.21.1+5, 1.21.8–26.2-snapshot-6 on Fabric, Quilt
- Updated to 26.2 (snapshot-6)
  - **WARNING:** the dependencies used at the time of writing (YACL `3.9.3` + Mod Menu `19.0.0-alpha.1`) are not fully ported to 26.2 and *will crash your game* 
    if you press the <kbd>Mods</kbd> button
- Going forward, Minecraft versions will be published with the `{year}.{drop}.x` pattern, meaning Chat Patches releases can automatically support hotfixes for
  specific drops (unless explicitly removed due to incompatibilities)! This will save both me (the developer) and you (the player) time in between drops, so 
  you can keep using the mod and I can avoid no-change releases
- Based on the relative lack of downloads compared to newer versions, the amount of version targets has been reduced. This should somewhat accelerate
  development for future releases! Later, I may reduce the amount of targets further. **This will not affect the latest version!**
- Ampersand formatting codes (ex `&a`) are now case-sensitive (`&A` will no longer work) — you shouldn't have done this in the first place though
- Revamped the process that generates the copiable text for the 'Formatted' menu option, and fixed some bugs within it ([#311](https://www.github.com/mrbuilder1961/ChatPatches/issues/311)). The results coming from it 
  should now be much more accurate and should contain less (if not zero) redundant formatting codes!
  - As a part of this, I introduced a slightly different approach that should work more effectively on modern Minecraft; however, it hasn't been tested much
  - Another feature pertaining to this is also coming soon!
- **Dev notes:**
  - In response to Mojang gutting `ChatFormatting` and changing `TextColor`, I made a new `Colors` util class to ease interoperability
  - In order to support the new consistent semver versioning and my existing publishing setup, versions can now specify `mod.versions` explicitly for CF and MR
    - `mod.nonReleaseComponent` can also be specified for snapshot development, so versions can be `{year}.{drop}` instead of the volatile 
    `{year}.{drop}-snapshot-n`. This is likely buggy

## Chat Patches `8.0-beta.1` for Minecraft 1.20.1, 1.21.1+5, 1.21.8–26.1 on Fabric, Quilt
(moved temporarily for hotfix compat w 26.1.2)
- [!NOT DONE] This release is the first to feature NeoForge compatibility! This will initially take time to acclimate to, but then dev time will decrease again
- [!NOT DONE] Implemented a budding test suite! If you're a player you won't firsthand see any of this, but you will feel its effects in that trivial bugs 
  should be caught earlier, before being shipped as releases!
- [!NOT DONE] Implemented a minimal DataFixerUpper system that automatically renames old config options and transforms anything necessary (right now, just 
  flips a boolean lol)
- For these reasons, I think it's fitting to bump this channel to beta!

## Chat Patches `8.0-alpha.8` for Minecraft 1.20.1–26.1 on Fabric, Quilt
- Updated to 26.1! Like always with recent Minecraft drops, A LOT has changed under the hood, so be on the lookout for bugs!
- Requires Fabric Loader 0.18.0+ going forward
- Added *another* Band-Aid patch for [#252](https://www.github.com/mrbuilder1961/ChatPatches/issues/252), again thanks to [SkyNotTheLimit](https://discord.com/channels/507304429255393322/1452165613756879044/1452516265330475085)!
- Added the Delete button to the context menu! When clicked it displays a warning forcing you to confirm or cancel the operation, because message deletion 
  cannot be undone. The burn item sound will play when a message is successfully deleted as an extra indicator something happened. **To be clear this is 
  purely client side**, _nothing is actually deleted_ from the server chat
- Fixed non-vanilla messages formatted like vanilla ones having an extra space between the name and message ([#233](https://www.github.com/mrbuilder1961/ChatPatches/issues/233))
- Mostly fixed Chat Heads' `BEFORE_NAME` setting not working with Chat Patches on 1.21.9+, thanks mostly to
   [Fourmisain](https://github.com/Fourmisain)! ([#285](https://www.github.com/mrbuilder1961/ChatPatches/issues/285) + [#297](https://www.github.com/mrbuilder1961/ChatPatches/issues/297), and likely [#233](https://www.github.com/mrbuilder1961/ChatPatches/issues/233) too!)
- Fixed a 1.21.10 crash from right-clicking to open the context menu ([#298](https://www.github.com/mrbuilder1961/ChatPatches/issues/298))
- Error and info toasts are now pushed on the render thread ([#302](https://www.github.com/mrbuilder1961/ChatPatches/issues/302))
- Fixed the duplicate counter not working with `counterCheckStyle` enabled ([#258](https://www.github.com/mrbuilder1961/ChatPatches/issues/258))
	- Note: historically the duplicate counter suffers from bad edge-cases, so if yours doesn't work with specific messages please report them under #258
- Fixed the config not writing default values to disk
- Optimized the dynamic shift calculator - it no longer runs every render tick!
- Once I get started on the compatibility with Neoforge (which should be soon!) I'll bump these releases to beta
- Also, subtle heads up that some versions that have long been old will lose long-term support soon. I'll post about this in that first release w/o those 
  versions and on the Discord, so make sure to join for more info and to make your voice heard!
- Fixed the boundary line not condensing when there are multiple back-to-back

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
- Added the `searchPrefix` config option, which controls whether to filter previously sent messages based on the text behind t