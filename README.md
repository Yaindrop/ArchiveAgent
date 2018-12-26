# ArchiveAgent
## About
A file back-up software that monitors file changes in multiple folders and copies new versions of file to the archive folder.

Provides a web-based GUI for status-checking and file management. 

Based on Java 11. Tested under Windows 10.

## Usage
[Download and unzip the runnable release](https://github.com/Yaindrop/ArchiveAgent/releases); Alternatively, you can build this project with IntelliJ IDEA.

Double-click the ArchiveAgent.jar to run; an icon should appear on the Taskbar.
![Icon](https://raw.githubusercontent.com/Yaindrop/ArchiveAgent/master/docs/img/1.jpg "Icon")

Double-clicking the Taskbar icon opens the console in your brower.
![console](https://raw.githubusercontent.com/Yaindrop/ArchiveAgent/master/docs/img/2.jpg "console")

Click "Set Archive Folder" to initialize the agent; then, click "Add New Folder" to add the folders you want to monitor. The agent will start back-uping the files to its archive folder.
![After Add](https://raw.githubusercontent.com/Yaindrop/ArchiveAgent/master/docs/img/3.jpg "After Add")

Clicking the watching folder path leads to the detailed page which lists all archived record for that folder.
![Details](https://raw.githubusercontent.com/Yaindrop/ArchiveAgent/master/docs/img/4.jpg "Details")

Select a file or record to restore it from the archive, or click "Restore All" to restore the entire folder.

## Warning
This software is in its earliest stage of development.

Do not use it for purposes other than learning or developing.

The developer is not responsible for any expected or unexpected consequence it causes.
