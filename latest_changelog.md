## Changes with this version
(this bugfix version is equal to 192.2.1)
- Fixed the config not always writing to disk
- Removed one-use constants from Config
- Renamed the Option subclass to ConfigOption to avoid conflicting with YACL's Option
- No longer removes the `INIT` flag every time a world is loaded