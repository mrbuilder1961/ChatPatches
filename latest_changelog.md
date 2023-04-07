## Changes with this version
- Updated to FAPI 0.77.0 and YACL 2.4.0
- Added the `regex` and `regex_tester` links in the YACL Help category for web links to regex assistance.
- Added the new ChatSearchScreen screen, an extension of ChatScreen that allows for searching through chat messages plus more features!
- Added the `chatSearchScreen` option that toggles the new chat search screen (which overrides the vanilla chat screen)
- Added a new mixin injector to override the vanilla chat screen with the new one (if enabled)
- Added some new String util methods
- Fixed the '&<?>'-formatted string to text parser not removing double backslashes from literal uses of ampersands
- Added a button icon and a background sprite

This update officially fixes [#4](https://www.github.com/mrbuilder1961/ChatPatches/issues/4) !!! sorry for taking so long, but hey it's done now!