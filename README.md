[![Fabric Mod](https://img.shields.io/badge/modloader-fabric-eeeeee)](https://fabricmc.net/use/)
[![Latest Version](https://img.shields.io/badge/version-1.5.3-blueviolet)](https://github.com/mrbuilder1961/WheresMyChatHistory/releases)
[![Curseforge Download](https://img.shields.io/badge/curseforge-5900_downloads-blue)](https://www.curseforge.com/minecraft/mc-mods/wmch)
[![Modrinth Download](https://img.shields.io/badge/dynamic/json?color=brightgreen&label=modrinth&suffix=%20downloads&query=downloads&url=https://api.modrinth.com/v2/project/MOqt4Z5n&cacheSeconds=3600)](https://www.modrinth.com/mod/MOqt4Z5n)
# Where's My Chat History

This mod does quite a few things:
- Increases the maximum amount of chat messages to 16384
- Adds a timestamp in front of all messages, formatted as `[HOUR:MINUTE:SECOND]` in pink text, with a tooltip that renders a complete date down to the millisecond
- Keeps chat history and previously sent messages across ALL worlds and servers
- Lets you modify vanilla player names to something more appealing than triangle brackets
- Minimize spam by enabling the counter that shows how many messages have been sent in a row
- All of this is configurable, with much more to offer!

Be on the lookout for new versions, issues, and possible future features!

## Setup developer workspace

1. Download the latest ZIP (this page -> Code -> Download ZIP)
2. Extract it to your desired folder
3. Open a terminal/command prompt, then run `./gradlew build`
4. Hopefully everything works!

## Configuration help

| Name | Default Value | Description | Example | Lang Key |
|---|---|---|---|---|
| Timestamp toggle | `true` | Should a timestamp in front of all messages show? | `false` | text.wmch.time |
| Timestamp text | `[HH:mm:ss]` | The text that is used for the timestamp. For dynamic text, search online for 'java date format string'. | `HH:mm` | text.wmch.timeStr |
| Timestamp color | `0xFF55FF` (`16733695`) | The color that's 'filled in' where applicable in the timestamp text. | `0x298570` | text.wmch.timeColor |
| Timestamp modifiers | ` ` | The string of ampersands (&) and formatting codes that apply over the time string. | `&l&o` | text.wmch.timeFormat |
| Hover toggle | `true` | Should the text that appears when you hover over the timestamp show? | `false` | text.wmch.hover |
| Hover text | `MM/dd/yyyy` | The text that appears when hovering over timestamp text. For dynamic text, search online for 'java date format string'. | `dd/MM/yyyy` | text.wmch.hoverStr |
| Message counter toggle | `true` | Should a message counter show after a message to indicate multiple were sent? | `false` | text.wmch.counter |
| Message counter text | `&8(&7x&e$&8)` | The text that's added to the end of a message to indicate multiple duplicates were sent. Must include a $ for the number of duplicates. | `x$` | text.wmch.counterStr |
| Message counter color | `0xFFFF55` (`16777045`) | The color that's 'filled in' where applicable in the counter text, for example '&r' will be replaced with this color. | `0x782235` | text.wmch.counterColor |
| Boundary toggle | `true` | Should a boundary line show after using chat, leaving, and then joining again later? | `false` | text.wmch.boundary |
| Boundary text | `&b[==============]` | The text that is shown when a boundary line is inserted into the chat, add ampersands (&) followed by a formatting code to prettify it. | `&l>-==-<` | text.wmch.boundaryStr |
| Boundary color | `0xAAAA` (`43690`) | The color that's 'filled in' where applicable in the boundary text, for example '&r' will be replaced with this color. | `0x14099062` | text.wmch.boundaryColor |
| Chat log toggle | `false` | Should the chat be saved into a log so it can be re-added back into the chat in another game session? | `true` | text.wmch.saveChat |
| Shift chat interface | `true` | Should that chat interface be shifted up about 10 pixels to not obstruct the armor bar? | `false` | text.wmch.shiftHudPos |
| Playername text | `<$>` | The text that replaces the playername in chat messages. Vanilla is <$>, no brackets is $; where $ represents the playername. Only applies to player messages. | `[$]:` | text.wmch.nameStr |
| Maximum chat messages | `16384` | The max amount of chat messages allowed to render. Vanilla has this set as 100, maximum allowed is 32,767. Keep in mind the higher the value, the more memory the chat requires. | `2167` | text.wmch.maxMsgs |

## Possible features

- Make certain messages copiable by default (on-click), such as the Open-To-LAN port
- Chat search feature, with regex search capabilities
- animate message recieve
- add buttons to the edge of chat input box OR in multiplayer menu screen (for WMCH settings and chat searcher)
- buttons/variable input strings to easily message data (ex. coords or UUID)
- "smart" chat shift feature, so eating golden apples/having more health doesn't get hidden behind the chat

## Changelog

See [individual releases](https://github.com/mrbuilder1961/WheresMyChatHistory/releases) for specific changelogs or the [commit list](https://github.com/mrbuilder1961/WheresMyChatHistory/commits).

## Wont fix/add

1. When you hover over the timestamp, by default it shows more detailed time information.
I was going to implement a system that, when the `hover` option was toggled off, it would just not render
the text rather than not add the HoverEvent entirely; so if it's toggled back on it would render. However,
unless I've glossed over a simpler method, this would take up wayyy too much extra memory just for this purpose, so that will not be added.
This also applies for most other options: if you toggle it off, it will probably not work on old messages if you toggle it back on again.

## License
This mod is available under the GNU LGPLv3 license. Check out [this](https://choosealicense.com/licenses/lgpl-3.0/) page for proper information.
