[![Fabric Mod](https://img.shields.io/badge/modloader-fabric-eeeeee)](https://fabricmc.net/use/)
[![Latest Version](https://img.shields.io/badge/version-1.19.3--1.5.7-blueviolet)](https://github.com/mrbuilder1961/WheresMyChatHistory/releases)
[![Curseforge Download](https://cf.way2muchnoise.eu/full_560042_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/wmch)
[![Modrinth Download](https://img.shields.io/badge/dynamic/json?&color=242629&labelColor=5ca424&label=modrinth&suffix=%20downloads&query=downloads&url=https://api.modrinth.com/v2/project/MOqt4Z5n&cacheSeconds=3600)](https://www.modrinth.com/mod/MOqt4Z5n)
# Where's My Chat History

This mod does quite a few things:
- Increases the maximum amount of chat messages to 16,384
- Adds a timestamp in front of all messages, formatted as `[HOUR:MINUTE:SECOND]` in pink text, with a tooltip that renders a complete date
- Keeps chat history and previously sent messages across ALL worlds and servers
- Lets you modify vanilla player names to anything more appealing than triangle brackets
- Minimize spam by enabling the counter that shows how many duplicate messages have been sent in a row
- Shift the chat hud up by 10 pixels so it doesn't overlap with the armor bar
- All of this is configurable, with much more to offer!

Be on the lookout for new versions, issues, and possible future features!

## FAQ

- What Minecraft version can I use this with? 
  - The *latest Fabric* versions of Minecraft, plus 1.18.x and soon 1.16.x. *Quilt* appears to work, but I don't provide official support for it.
- Will you make it for (older Minecraft version)? 
  - Not likely. The oldest version I'll be making is 1.16.x.
- Will you make this for Forge? 
  - I doubt it. Fabric's API is simple and I don't feel like learning about it.
- I found a bug/issue, what do I do? 
  - First, make sure you have the latest version of the mod for your Minecraft version. If it still persists, you can click on the Issues tab on this page, and nobody has opened one about your issue then create one! Note that a lack of provided information will increase time needed to fix.
- It says I need to install Fabric but I already have it installed?
  - [Install the Fabric API.](https://modrinth.com/mod/fabric-api)
- Do servers need this mod? 
  - It is client side, so no.
- I can't access the config menu in-game! 
  - You need the LATEST versions of both [Cloth Config](https://modrinth.com/mod/cloth-config/versions) and [Mod Menu](https://modrinth.com/mod/modmenu/versions) to access it in game. Alternatively, you can edit it manually at `(directory)/config/wmch.json`.
- My config is broken/not working!
  - When you load one of the more recent mod versions, it makes a creates a new file with your old settings and resets the main file. You can access this copy at `(directory)/config/wmch_old.json`. By then opening both the new and old configs at the same time, you can copy and paste over the values. Make sure the settings have correct inputs (ex. `time` should be `true`/`false` not `123` or `"true"`). If you still are having problems, open a new issue on GitHub.

## Localization and Translation

If you would like to help translate WMCH into other languages, you can:
- Create a pull request that adds or corrects a language file
- Head over to [the CrowdIn page](https://crwd.in/wmch) and see how you can contribute

## Setup devenv / Build latest jars

1. Download the latest ZIP (this page -> Code -> Download ZIP)
2. Extract it to your desired folder
3. Open a terminal/command prompt, then run `./gradlew build`

4a. For a devenv, open the folder in your IDE

4b. For built jars, navigate to /build/libs/

## Configuration help

| Name                      | Default Value           | Description                                                                                                                                                                                                  | Lang Key                  |
|---------------------------|-------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------|
| Timestamp toggle          | `true`                  | Should a timestamp in front of all messages show?                                                                                                                                                            | `text.wmch.time`          |
| Hover toggle              | `true`                  | Should text appear stating a more detailed time description when you hover over the timestamp text in chat?                                                                                                  | `text.wmch.hover`         |
| Message counter toggle    | `true`                  | Should a message counter show after messages to indicate multiple duplicates were sent?                                                                                                                      | `text.wmch.counter`       |
| Boundary toggle           | `true`                  | Should a boundary line show after chatting, leaving a world/server, and then joining another world/server later?                                                                                             | `text.wmch.boundary`      |
| Timestamp text            | `HH:mm:ss`              | The text that is formatted into a timestamp.                                                                                                                                                                 | `text.wmch.timeDate`      |
| Hover text                | `MM/dd/yyyy`            | The text that is formatted into a longer date/time string.                                                                                                                                                   | `text.wmch.hoverDate`     |
| Message counter text      | `&8(&7x&e$&8)`          | The text that's added to the end of a message to indicate multiple duplicates were sent. Requires a $ for the number of duplicates, also supports '&'s + formatting codes to modify it.                      | `text.wmch.counterStr`    |
| Boundary text             | `&8[&b$&8]`             | The text that is formatted and used for the boundary line. Supports '&' + formatting codes to modify it.                                                                                                     | `text.wmch.boundaryStr`   |
| Timestamp decoration text | `[$]`                   | The string of '&'s + formatting codes and decoration text that create the timestamp. The $ is a placeholder for the formatted timestamp Option above this one.                                               | `text.wmch.timeFormat`    |
| Hover decoration text     | `$`                     | The string of '&'s + formatting codes and decoration text that create the detailed hover description. Requires a $ as a placeholder for the formatted timestamp Option above this one.                       | `text.wmch.hoverFormat`   |
| Timestamp color           | `0xFF55FF` (`16733695`) | The color that's filled in where it would otherwise be blank white in the resulting timestamp.                                                                                                               | `text.wmch.timeColor`     |
| Hover text color          | `0xFFFFFF` (`16777215`) | The color that's filled in where it would otherwise be blank white in the resulting hover text.                                                                                                              | `text.wmch.hoverColor`    |
| Message counter color     | `0xFFFF55` (`16777045`) | The color that's filled in where it would otherwise be blank white in the resulting duplicate counter.                                                                                                       | `text.wmch.counterColor`  |
| Boundary color            | `0x55FFFF` (`5636095`)  | The color that's filled in where it would otherwise be blank white in the resulting boundary line.                                                                                                           | `text.wmch.boundaryColor` |
| Chat log toggle           | `true`                  | Should the chat be saved into a log so it can be re-added back into the chat in another game session?                                                                                                        | `text.wmch.saveChat`      |
| Shift chat                | `10`                    | Shifts the chat interface up to not obstruct the armor bar and/or health. Default is 10, set to 0 for no shift.                                                                                              | `text.wmch.shiftChat`     |
| Playername text           | `$`                     | The text that replaces the playername in chat messages. Vanilla is <$>, no triangle brackets is $; where $ is a placeholder for the playername. Only applies to player sent messages.                        | `text.wmch.nameStr`       |
| Maximum chat messages     | `0x4000`                | The max amount of chat messages allowed to render. Vanilla is capped at 100, the true maximum is 32767. Keep in mind the higher the number, the more memory the chat will require to store all the messages. | `text.wmch.maxMsgs`       |

## Possible features
- Chat search screen, with regex search capabilities
- animate message receive
- add buttons to the edge of chat input box OR in multiplayer menu screen (for WMCH settings and chat searcher)
- buttons/variable input strings to easily message data (ex. coords or UUID)

## Incompatibilities

- Meteor Client's BetterChat features
- MoreChatHistory

## Won't fix/add

1. When you hover over the timestamp, by default it shows more detailed time information.
   I was going to implement a system that, when the `hover` option was toggled off, it would just not render the text rather than not add the HoverEvent entirely; so if it's toggled back on it would render. However, unless I've glossed over a simpler method, this would take up wayyy too much extra memory just for this purpose, so that will not be added. This also applies for most other options: if you toggle it off, it will probably not work on old messages if you toggle it back on again.

## Changelog

See [individual releases](https://github.com/mrbuilder1961/WheresMyChatHistory/releases) for specific changelogs or the [commit list](https://github.com/mrbuilder1961/WheresMyChatHistory/commits).

## License
This mod is available under the GNU LGPLv3 license. Check out [this](https://choosealicense.com/licenses/lgpl-3.0/) page for information.
