[![Fabric Mod](https://img.shields.io/badge/modloader-fabric-informational)](https://fabricmc.net/use/)
[![Latest Version](https://img.shields.io/badge/version-1.4.4-brightgreen)](https://github.com/Giggitybyte/ServerChatHistory/releases)
[![Curseforge Download](https://bit.ly/33eX25e)](https://www.curseforge.com/minecraft/mc-mods/wmch)
# Where's My Chat History

This mod does quite a few things:
- Increases the maximum amount of chat based on allocated RAM (minimally it goes from 100 -> 1024 max messages)
- Adds a timestamp in front of all messages, formatted like `[HOUR:MINUTE:SECOND]` in pink text, with a tooltip that renders a complete date down to the millisecond
- Keeps chat history and previously sent messages across ALL world/servers (but automatically resets on game exit)
- All of this is configurable

Be on the lookout for new versions, issues, and possible future features!

## Setup

1. [Download](https://github.com/mrbuilder1961/WheresMyChatHistory/archive/refs/heads/1.18.x.zip) the ZIP
2. Extract it to your desired folder
3. Open a terminal/command prompt, then run `./gradlew build`
4. Hopefully everything works!

If that doesn't work, then I don't know, just trial-and-error things? Do whatever you'd normally do? I honestly don't know.

## Configuration help

| Name | Default Value | Description | Example | Lang Key |
|---|---|---|---|---|
| Timestamp toggle | `true` | Should the timestamp in front of all messages show? | `false` | text.wmch.time |
| Timestamp text | `"[HH:mm:ss]"` | The text that is used for the timestamp. To add time values, see [https://i.imgur.com/KlBIDWf.png]. | `"'TIME'- HH:mm"` | text.wmch.timeStr |
| Timestamp color | `0xFF55FF` (`16733695`) | The color that is applied BEFORE the timestamp modifiers, while also overriding any colors provided there. | `10743823` | text.wmch.timeColor |
| Timestamp modifiers | `[]` | The array of Formattings to modify the looks of the timestamp's text. See the name values at [https://minecraft.fandom.com/wiki/Formatting_codes]. | `["obfuscated"]` | text.wmch.timeFormatting |
| Hover toggle | `true` | Should the text that appears when you hover over the timestamp show? | `false` | text.wmch.hover |
| Hover text | `"yyyy dd, MM, @ HH:mm:ss.SSSS"` | The text that shows when you hover over the timestamp in front of all chat messages. For dynamic text, see [https://i.imgur.com/KlBIDWf.png]. | `"MM/DD/YYYY"` | text.wmch.hoverStr |
| Boundary toggle | `true` | Should a boundary line show after using chat and leaving then joining again later? | `false` | text.wmch.boundary |
| Boundary text | `"<]===---{ SESSION BOUNDARY LINE }---===[>"` | The text that is shown when a boundary line is inserted into the chat. | `"world/session barrier"` | text.wmch.boundaryStr |
| Boundary color | `0x00AAAA` (`43690`) | The color that is applied BEFORE the boundary modifiers, while also overriding any colors provided there. | `15732643` | text.wmch.boundaryColor |
| Boundary modifiers | `[]` | The array of Formattings to modify the looks of the boundary text. Ignores color values (Boundary color). See the name values at [https://minecraft.fandom.com/wiki/Formatting_codes]. | `["bold", "underline"]` | text.wmch.boundaryFormatting |
| Playername text | `"<$>"` | The text that replaces the playername in most references (if present). No brackets would be $; as $ represents the playername. Only for player messages. | `"[$]"` | text.wmch.nameStr |
| Maximum chat messages | `1024` | The max amount of chat messages allowed to render. Not recommended to change, as it's automatically adjusted based on RAM allocation. | `5237` | text.wmch.maxMsgs |
| Reset all settings | `false` | If this is toggled on, it will reset ALL options to their default values. | `true` | text.wmch.reset |

## Possible features

- Make certain messages automatically copiable, such as the Open-To-LAN port
- Message counter (x2 or x3 appearing after multiple messages)
- Make Fabric API optional, but disables boundary messages
- Chat search feature, with regex search capabilities
- Modrinth page
- Smooth message receive

## Changelog

See [individual releases](https://github.com/mrbuilder1961/WheresMyChatHistory/releases) for specific changelogs or the [commit list](https://github.com/mrbuilder1961/WheresMyChatHistory/commits/1.18.x). A complete one may be made at some point.

## Wont fix/add

When you hover over the timestamp, by default it shows more detailed time information.
I was going to implement a system that, when the `hover` option was toggled off, it would just not render
the text rather than not add the HoverEvent entirely; so if it's toggled back on it would render. However,
unless I've glossed over a simpler method, this would take up wayyy too much extra memory just for this purpose, so that will not be added.
This also applies for all other options: if you toggle it off, it will probably not work on old messages if you toggle it back on again.

## License
This mod is available under the GNU GPLv3 license. TL;DR: You can do whatever, as long as it's free, open-source, and credits me and the other authors.
