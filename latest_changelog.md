## Changes with this version
- Update to 1.19.4 and mark [#68](https://www.github.com/mrbuilder1961/ChatPatches/issues/68) complete (compatible with 1.19.3!)
- Switch `ChatHudAccessor` mixin to a duck interface instead
- Added and updated some more documentation
- Updated Gradle to v8.0.1
- Updated to YACL `2.3.0` and Mod Menu `6.1.0-rc.4` (warning: this version depends on a pre-release of Mod Menu, so it may be buggy or weird!)
- Now requires Fabric Loader `0.14.17` or higher
- Completely overhauled the Util class and split it into a bunch of smaller, more specific classes.
- Added a new MessageData class for only storing the necessary data needed for a message. this will help backporting efforts in the future!
- Restored messages no longer clog the logs with messages about being restored ([#65](https://www.github.com/mrbuilder1961/ChatPatches/issues/65))
- Changed the default mixin priority to `2000` from `400` to partially fix [#66](https://www.github.com/mrbuilder1961/ChatPatches/issues/66) for 1.19.3 and 1.19.4. This might cause issues with other mods, so keep an eye out for issues, and make sure to report them if you find any!
- Added a try-catch block to `ChatHudMixin#addCounter` to prevent the chat from effectively disabling if an error occurs (part 2 of [#66](https://www.github.com/mrbuilder1961/ChatPatches/issues/66))

btw sorry about the huge commit, I just really wanted to get this pushed out, and it took a lot longer than I was hoping. I'll try to keep the commits smaller in the future.