## Changes with this version
- Fixed the config not always writing to disk
- Removed one-use constants from Config
- Renamed the Option subclass to ConfigOption to avoid conflicting with YACL's Option
- No longer removes the `INIT` flag every time a world is loaded