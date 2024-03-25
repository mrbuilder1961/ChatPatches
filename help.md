# Help

## How to upload log files
There are multiple different types of log file that can be uploaded, although they are all very similar. In order of most useful to least, these are the three
main types:
1. `debug.log` - Generated when the game is configured to output on the `DEBUG` level. It isn't always present, but when it is, it almost always has a ton of
   extra information that can be critical to solving issues. This is a more detailed subset of the `latest.log`.
2. `crash-report-<date>-<time>.txt` - Generated when the game crashes; contains a lot of extra information not provided in the other two logs, however it is
   most useful when coupled with one of the other two logs.
3. `latest.log` - The main log file that is generated every time the game is run. It constantly updates, and contains plenty of useful information on what
   the game is doing.

Generally, you should upload one `.log` file and one `crash-report-<date>-<time>.txt` file if you have them. Otherwise, just upload whatever you have that
is listed above. The only thing you shouldn't upload is both `debug.log` and `latest.log`, as the `debug.log` has everything the `latest.log` has and more.
*TL;DR upload as much as you can, but don't upload both the `debug.log` and `latest.log` files.*

Now that you have the files, simply submitting them to https://mclo.gs/ will give you a link that you can share with me and others to help diagnose your issue.
It is really the only site I use, because it provides a lot of useful tools both for me and for you. One of those being that it censors many instances of
potentially identifiable information, such as your computer's username and IP address.

### To upload the log(s) without Prism Launcher:
1. Go to https://mclo.gs/
2. Click the "Select a file" button, and select a file. **OR** Paste the contents of the log file into the text box.
3. Click the "Save" button.
4. Wait for a moment, and it will redirect you to your uploaded log file. Copy the URL from the address bar and paste it wherever you were asked or need to
   share it.

### With Prism Launcher:
1. Open Prism Launcher.
2. Click on your instance that contains the log you want to upload.
3. Navigate to the "Minecraft Log" tab (or "Other logs" tab if it's not there)
4. Click the "Upload" button when the log you want to upload is shown.
5. Click the "Yes" button in response to the security prompt. If you're worried about the security of the log, you can always censor it yourself before
   uploading it.
6. The log should now be uploaded, and you can click the blue underlined text to view it in your browser. The URL is automatically copied to your clipboard,
   so you can now share it wherever you were asked or need to.

## How to access beta releases
To access beta releases, you'll need a link that looks like this:

https://github.com/mrbuilder1961/ChatPatches/actions/runs/8310511511/ or

https://github.com/mrbuilder1961/ChatPatches/actions/runs/8310511511/artifacts/1332245802

### Now, follow these steps:
1. Click on the link. If it doesn't instantly download, scroll down, and under the "Artifacts" section, click on "jars" and the download should start.
2. Once the download is complete, open or extract the .zip file.
3. You should see two folders, `libs` and `devlibs`. Open the `libs` folder.
4. Inside the `libs` folder, you should see a .jar file, and another file with the same name but instead ends with `-sources.jar`. The .jar file is the mod itself, and the `-sources.jar` file is the source code.
5. Move the .jar file, NOT the `-sources` file, to your mods folder.
6. If you have another version of Chat Patches already installed there, you can move it, rename the file extension to anything but `.jar`, or delete it.
7. Now you should be good to go! Launching the game should now load the beta version. If you experience any issues, make sure to report them as soon as
   possible wherever you were given the link (here on GitHub or [the Discord](https://discord.gg/3MqBvNEyMz)).