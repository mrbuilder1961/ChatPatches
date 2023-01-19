In this update, I bring to you a per-version changelog, which is updated before every published release!
This marks the end of GitHub releases as they are not really worth it, and you can just check the Actions tab for the latest builds.
And now, the majority of CurseForge and Modrinth updates will be automatic!

### Changes with this version:
- Added CurseForge and Modrinth publishing modules to `build.gradle`, usable through `.\gradle publish`
- Added this changelog
- Changed versioning scheme: `MCVERSION.FEATURE.BUGFIX`, where `MCVERSION` is part of a Minecraft version, `FEATURE` is a big fix/change/suggestion, and 
  `BUGFIX` is a small bugfix. ex. `19.1.0` (any vers of 1.19, some new feature, no bugfixes), or `182.0.1` (1.18.2, no new features, some bugfixes)