[![Fabric Mod](https://img.shields.io/badge/modloader-fabric-informational)](https://fabricmc.net/use/)
[![Latest Version](https://img.shields.io/badge/version-1.4.4-brightgreen)](https://github.com/Giggitybyte/ServerChatHistory/releases)
[![Curseforge Download](https://bit.ly/33eX25e)](https://www.curseforge.com/minecraft/mc-mods/wmch)
# Where's My Chat History

This mod does quite a few things:
- Increases the maximum amount of chat based on allocated RAM (minimally it goes from 100 -> 1024 max messages)
- Adds a timestamp in front of all messages, formatted like [HOUR:MINUTE:SECOND] in pink text, with a tooltip that renders a complete date down to the millisecond
- Keeps chat history and previously sent messages across ALL world/servers (but automatically resets on game exit)
- All of this is configurable

Be on the lookout for new versions, issues, and possible future features!

## Setup

1. [Download](https://github.com/mrbuilder1961/WheresMyChatHistory/archive/refs/heads/1.18.x.zip) the ZIP
2. Extract it to your desired folder
3. Open a terminal/command prompt, then run `./gradlew build`
4. Hopefully everything works!

If that doesn't work, then I don't know, just trial-and-error things? Do whatever you'd normally do? I honestly don't know.

## Possible features

- Chat search box, with regex search capabilities
- Message counter (x2 or x3 appearing after multiple messages)
- Smooth message receive
- Modrinth page

## Changelog

See individual releases for specific changelogs, a full one will be made at some point.

## Wont fix/add

When you hover over the timestamp, by default it shows more detailed time information.
I was going to implement a system that, when the `hover` option was toggled off, it would just not render
the text rather than not add the HoverEvent entirely; so if it's toggled back on it would render. However,
unless I've glossed over a simpler method, this would take up wayyy too much extra memory just for this purpose, so that will not be added.
This also applies for all other options: if you toggle it off, it will probably not work on old messages if you toggle it back on again.

## License
This mod is available under the GNU GPLv3 license. TL;DR: You can do whatever, as long as it's free, open-source, and credits me and the other authors.
