## Changes with this version
### Rebranding
- Rebranded to "Chat Patches" from "Where's My Chat History?" to better reflect the purpose of the mod
- Also changed the author's (my) name back to OBro1961
- Both of these changes are reflected in all references, including the repository, code, and sites on which it is hosted
- Make sure to update any sort of references you may have as well!
- This **DOES** change the config file used! Make sure to rename `wmch.json` to `chatpatches.json` in your `config` folder!

### Technical
- Split the mixins inside the `mixin` folder into three separate folders: `chat`, `secure`, and the main `mixin` folder
- `chat` is self-explanatory, `secure` contains mixins relating to the Secure Chat features of 1.19, and the main is for one-off mixins
- Shortened some mixin method names

### Other
- Renamed "Chat Interface" category to "Chat Hud"
- Added english translation for the Debug category

### Differences
name: `Where's My Chat History?` => `Chat Patches`  
id: `wmch` => `chatpatches`  
capitalized: `WMCH` => `ChatPatches`  
config: `wmch.json` => `chatpatches.json`  
mixin method prefix: `wmch$` => `cps$`