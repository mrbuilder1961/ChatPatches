[![Fabric Mod](https://img.shields.io/badge/modloader-fabric-informational)](https://fabricmc.net/use/)
[![Latest Version](https://img.shields.io/badge/version-1.5-brightgreen)](https://github.com/mrbuilder1961/WheresMyChatHistory/releases)
[![Curseforge Download](https://bit.ly/33eX25e)](https://www.curseforge.com/minecraft/mc-mods/wmch)
# Where's My Chat History

This mod does quite a few things:
- Increases the maximum amount of chat messages allowed based on allocated RAM (minimally it goes from 100 to 1024 max messages)
- Adds a timestamp in front of all messages, formatted as `[HOUR:MINUTE:SECOND]` in pink text, with a tooltip that renders a complete date down to the millisecond
- Keeps chat history and previously sent messages across ALL worlds and servers
- Lets you modify vanilla player names to something more appealing than triangle brackets
- Minimize spam by enabling the counter that shows how many messages have been sent in a row
- All of this is configurable, with much more to offer

Be on the lookout for new versions, issues, and possible future features!

## Setup developer workspace

1. Download the latest ZIP (this page -> Code -> Download ZIP)
2. Extract it to your desired folder
3. Open a terminal/command prompt, then run `./gradlew build`
4. Hopefully everything works!

## Configuration help

| Name | Default Value | Description | Example | Lang Key |
|---|---|---|---|---|
| Timestamp toggle | `true` | Should a timestamp in front of all messages show? | `false` | `text.wmch.time` |
| Timestamp text | `[HH:mm:ss]` | The text that is used for the timestamp. For dynamic text, search online for 'java date format string'. | `HH:mm` | `text.wmch.timeStr` |
| Timestamp color | `0xFF55FF` (`16733695`) | The color that's 'filled in' where applicable in the timestamp text. | `298570` | `text.wmch.timeColor` |
| Timestamp modifiers | ` ` | The string of ampersands (&) and formatting codes that apply over the time string. | `&l&o` | `text.wmch.timeFormat` |
| Hover toggle | `true` | Should the text that appears when you hover over the timestamp show? | `false` | `text.wmch.hover` |
| Hover text | `yyyy dd, MM, @ HH:mm:ss.SSSS` | The text that appears when hovering over timestamp text. For dynamic text, search online for 'java date format string'. | `dd/MM/yyyy` | `text.wmch.hoverStr` |
| Message counter toggle | `true` | Should a message counter show after messages to indicate multiple were sent? | `false` | `text.wmch.counter` |
| Message counter text | `&8(&7x&e$&8)` | The text that's added to the end of messages to indicate multiple duplicates were sent. Must include a $ for the number of duplicates. | `x$` | `text.wmch.counterStr` |
| Message counter color | `0xFFFF55` (`16777045`) | The color that's 'filled in' where applicable in the counter text. | `782235` | `text.wmch.counterColor` |
| Boundary toggle | `true` | Should a boundary line show after using chat, leaving, and then joining again later? | `false` | `text.wmch.boundary` |
| Boundary text | `&b[==============]` | The text that is shown when a boundary line is inserted into the chat, add ampersands (&) followed by a formatting code to prettify it. | `&l>-==-<` | `text.wmch.boundaryStr` |
| Boundary color | `0xAAAA` (`43690`) | The color that's 'filled in' where applicable in the boundary text. | `14099062` | `text.wmch.boundaryColor` |
| Chat log toggle | `false` | Should the chat be saved into a log so it can be re-added back into the chat in another game session? | `true` | text.wmch.saveChat |
| Playername text | `<$>` | The text that replaces the playername in most references (if present). Vanilla would be <$>; no brackets $. $ represents the playername. Only for player messages. | `[$]:` | `text.wmch.nameStr` |
| Maximum chat messages | `1024` | The max amount of chat messages allowed to render. Not recommended to change, as it's automatically adjusted based on RAM allocation. | `2167` | `text.wmch.maxMsgs` |

## Possible features

- Make certain messages automatically copiable, such as the Open-To-LAN port
- Chat search feature, with regex search capabilities
- Modrinth page

## Changelog

See [individual releases](https://github.com/mrbuilder1961/WheresMyChatHistory/releases) for specific changelogs or the [commit list](https://github.com/mrbuilder1961/WheresMyChatHistory/commits). A complete one may be made at some point.

## Wont fix/add

1. When you hover over the timestamp, by default it shows more detailed time information.
I was going to implement a system that, when the `hover` option was toggled off, it would just not render
the text rather than not add the HoverEvent entirely; so if it's toggled back on it would render. However,
unless I've glossed over a simpler method, this would take up wayyy too much extra memory just for this purpose, so that will not be added.
This also applies for most other options: if you toggle it off, it will probably not work on old messages if you toggle it back on again.

## License
This mod is available under the GNU GPLv3 license. TL;DR: You can do whatever, as long as it's free, open-source, and credits me and the other authors.
