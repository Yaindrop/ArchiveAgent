window.ajaxGet = function (requestUrl, responseHandler) {
    var request;
    if (window.XMLHttpRequest) {
        request = new XMLHttpRequest();
    } else {
        var noAjaxAlert = "This page can't be loaded because AJAX is not supported by your browser.";
        window.alert(noAjaxAlert);
        return;
    }
    request.onreadystatechange = function () {
        if ((request.readyState === 4) && (request.status === 200)) 
            responseHandler(request.responseText);
    };
    request.open("GET", requestUrl, true);
    request.send(null);
};

window.onresize = function() {
    var w = window,
    d = document,
    e = d.documentElement,
    g = d.getElementsByTagName('body')[0],
    x = w.innerWidth || e.clientWidth || g.clientWidth;
    var title = d.getElementsByTagName("title")[0].innerHTML;
    var head = d.getElementById("head");
    if (x <= 700) {
        if (title.length > 15) {
            newSize = parseInt(150 / title.length * 10) / 10 + "vw";
            head.style.fontSize = newSize;
        }
    } else if (x <= 1400) {
        if (title.length > 24) {
            newSize = parseInt(144 / title.length * 10) / 10 + "vw";
            head.style.fontSize = newSize;
        }
    } else {
        head.style.fontSize = "3rem";
    }
}

window.toObj = function() {
    var res = {};

    var archive_folder = document.getElementById("archive_folder");
    if (archive_folder.classList.contains("uninitialized")) res["archive_folder"] = null;
    else res["archive_folder"] = archive_folder.innerHTML;

    var watching_folders = document.getElementsByClassName("watching_folder");
    res["watching_folders"] = [];
    for (var i = 0; i < watching_folders.length; i ++) 
        res["watching_folders"].push(watching_folders[i].innerHTML);

    var settings = document.getElementById("settings_list").getElementsByTagName("li");
    res["settings"] = [];
    for (var i = 0; i < settings.length; i ++) 
        res["settings"].push(settings[i].classList.contains("settings_enabled"));

    return res;
}

document.addEventListener("DOMContentLoaded", function(event) {
    window.onresize();
    var wsPort;
    var wsSocket

    var dom_current_status = document.getElementById("current_status");

    var dom_overview_page = document.getElementById("overview_page");
    dom_overview_page.style.display = "block";
    var dom_archive_folder = document.getElementById("archive_folder");
    var dom_watching_folders_list = document.getElementById("watching_folders_list");
    var dom_add_watching_folder = document.getElementById("add_watching_folder");
    var dom_move_archive_folder = document.getElementById("move_archive_folder");
    var dom_pause_syncing = document.getElementById("pause_syncing");
    var dom_settings_btn = document.getElementById("settings_btn");
    var dom_quit = document.getElementById("quit");

    var dom_watching_folder_page = document.getElementById("watching_folder_page");
    var dom_watching_folder_title = document.getElementById("watching_folder_title");
    var dom_files_list = document.getElementById("files_list");
    var dom_file_options = document.getElementById("file_options");
    var dom_file_restore = document.getElementById("file_restore");
    var dom_file_discard_all = document.getElementById("file_discard_all");
    var dom_file_clean = document.getElementById("file_clean");
    var dom_records_path = document.getElementById("records_path");
    var dom_records_list = document.getElementById("records_list");
    var dom_record_options = document.getElementById("record_options");
    var dom_record_restore = document.getElementById("record_restore");
    var dom_record_discard = document.getElementById("record_discard");
    var dom_restore_all = document.getElementById("restore_all");
    var dom_discard_all = document.getElementById("discard_all");
    var dom_clean_inactive = document.getElementById("clean_inactive");
    var dom_stop_watching = document.getElementById("stop_watching");

    var dom_settings_page = document.getElementById("settings_page");
    var dom_auto_syncing = document.getElementById("auto_syncing");
    var dom_launch_on_start = document.getElementById("launch_on_start");
    var dom_auto_discard_outdated = document.getElementById("auto_discard_outdated");
    var dom_auto_clean_inactive = document.getElementById("auto_clean_inactive");

    var dom_log_page = document.getElementById("log_page");
    dom_log_page.style.display = "none";
    var dom_server_log_list = document.getElementById("server_log_list");
    var dom_agent_log_list = document.getElementById("agent_log_list");

    var page = 0;

    var archive_folder;
    var paused = false;

    var watching_folder;
    var files;
    var selected_file;
    var selected_record;

    var auto_syncing = true;
    var launch_on_start = false;
    var auto_discard_outdated = false;
    var auto_clean_inactive = false;

    /* Message Handlers */

    var updateStatus = function(content) {
        dom_watching_folders_list.innerHTML = "";
        for (var i = 0; i < content.watching_dirs.length; i ++) {
            var li = document.createElement('li');
            var a = document.createElement('a');
            const path = content.watching_dirs[i];
            a.innerHTML = path;
            a.addEventListener("click", function() {
                clickWatchingFolder(path);
            });
            li.appendChild(a);
            dom_watching_folders_list.appendChild(li);
        }

        // updateSettings

        dom_current_status.innerHTML = content.idle ? "Idle" : "Busy";
        if (paused && content.syncing) setResumed();
        else if (!paused && !content.syncing) setPaused();
    }

    var updateOverview = function(content) {
        archive_folder = content;
        dom_archive_folder.innerHTML = archive_folder;
        dom_archive_folder.classList.remove("uninitialized");
        dom_add_watching_folder.classList.remove("disabled");

        dom_move_archive_folder.classList.remove("disabled");
        dom_pause_syncing.classList.remove("disabled");
        dom_settings_btn.classList.remove("disabled");
        dom_quit.classList.remove("disabled");
    }

    var setPaused = function () {
        paused = true;
        dom_pause_syncing.innerHTML = "Resume Syncing";
        dom_pause_syncing.title = "Resume auto syncing";
        dom_pause_syncing.classList.remove("disabled");
    }

    var setResumed = function() {
        paused = false;
        dom_pause_syncing.innerHTML = "Pause Syncing";
        dom_pause_syncing.title = "Pause auto syncing";
        dom_pause_syncing.classList.remove("disabled");
    }

    var switchToWatchingFolderPage = function() {
        dom_overview_page.style.display = "none";
        dom_watching_folder_page.style.display = "block";
        dom_watching_folder_title.innerHTML = watching_folder;
        dom_restore_all.classList.remove("disabled");
        dom_discard_all.classList.remove("disabled");
        dom_clean_inactive.classList.remove("disabled");
        dom_stop_watching.classList.remove("disabled");
        page = 1;
    }

    var switchToSettingsPage = function() {
        dom_overview_page.style.display = "none";
        dom_settings_page.style.display = "block";
        /*
        dom_auto_syncing.classList.remove("disabled");
        dom_launch_on_start.classList.remove("disabled");
        dom_auto_discard_outdated.classList.remove("disabled");
        dom_auto_clean_inactive.classList.remove("disabled");
        */
        page = 2;
    }

    var enableFileOptions = function(file) {
        dom_file_options.style.display = "block";
        dom_file_restore.classList.remove("disabled");
        dom_file_discard_all.classList.remove("disabled");
        if (!file.isActive) dom_file_clean.classList.remove("disabled");
    }

    var disableFileOptions = function() {
        dom_file_options.style.display = "none";
        dom_file_restore.classList.add("disabled");
        dom_file_discard_all.classList.add("disabled");
        dom_file_clean.classList.add("disabled");
    }

    var enableRecordOptions = function() {
        dom_record_options.style.display = "block";
        dom_record_restore.classList.remove("disabled");
        dom_record_discard.classList.remove("disabled");
    }

    var disableRecordOptions = function() {
        dom_record_options.style.display = "none";
        dom_record_restore.classList.add("disabled");
        dom_record_discard.classList.add("disabled");
    }

    var updateFilesList = function() {
        disableFileOptions();
        updateRecordList(null);
        dom_files_list.innerHTML = "";
        if (files.length === 0) {
            var li = document.createElement('li');
            li.innerHTML = "Empty";
            dom_files_list.appendChild(li);
        } else {
            for (var i = 0; i < files.length; i ++) {
                const file = files[i];
                var li = document.createElement('li');
                var a = document.createElement('a');
                a.innerHTML = file.path;
                a.classList.add("file");
                if (!file.isActive) a.classList.add("inactive");
                file.element = a;
                a.addEventListener("click", function() {
                    clickFile(file);
                });
                li.appendChild(a);
                dom_files_list.appendChild(li);
                if (selected_file && selected_file.path === file.path) clickFile(file)
            }
        }
    }

    var updateRecordList = function(file) {
        disableRecordOptions();
        selected_record = null;
        dom_records_path.innerHTML = "Select A File";
        dom_records_list.innerHTML = "";
        if (file != null) {
            dom_records_path.innerHTML = file.path;
            for (var i = 0; i < file.records.length; i ++) {
                const record = file.records[i];
                var li = document.createElement('li');
                var a = document.createElement('a');
                a.innerHTML = new Date(record.time);
                a.classList.add("record");
                record.element = a;
                a.addEventListener("click", function() {
                    clickRecord(record);
                });
                li.appendChild(a);
                dom_records_list.append(li);
            }
        }
    }
    /*
    var updateSettings = function(item, value) {
        dom_settings_a = dom_settings[item];
        if (dom_settings_a.classList.contains("settings_enabled") === value) return;
        switch(item) {
            case 1:

        }
    }*/

    var addLogLine = function(target_list) {
        var li = document.createElement('li');
        target_list.appendChild(li);
    }
    addLogLine(dom_server_log_list);
    addLogLine(dom_agent_log_list);

    /* Event Listeners */

    dom_current_status.addEventListener("click", function() {
        dom_log_page.style.display = dom_log_page.style.display === "none" ? "block" : "none";
        dom_server_log_list.lastChild.scrollIntoView();
        dom_agent_log_list.lastChild.scrollIntoView();
        dom_log_page.scrollIntoView();
    });

    // Overview

    var clickWatchingFolder = function(path) {
        watching_folder = path;
        sendMessage("check_watching_folder", path);
    }

    dom_archive_folder.addEventListener("click", function() {
        if (dom_archive_folder.classList.contains("disabled")) return;
        if (dom_archive_folder.classList.contains("uninitialized")) {
            sendMessage("initialize_archive_path", null);
        } else {
            sendMessage("open_archive_path", null);
        }
    });

    dom_add_watching_folder.addEventListener("click", function() {
        if (dom_add_watching_folder.classList.contains("disabled")) return;
        sendMessage("add_watching_folder", null);
    });

    dom_move_archive_folder.addEventListener("click", function() {
        if (dom_move_archive_folder.classList.contains("disabled")) return;
        sendMessage("move_archive_folder", null);
    });

    dom_pause_syncing.addEventListener("click", function() {
        if (dom_pause_syncing.classList.contains("disabled")) return;
        if (paused) sendMessage("resume_syncing", null);
        else sendMessage("pause_syncing", null);
        dom_pause_syncing.classList.add("disabled");
    });

    dom_settings_btn.addEventListener("click", function() {
        if (dom_settings_btn.classList.contains("disabled")) return;
        switchToSettingsPage();
    });

    dom_quit.addEventListener("click", function() {
        if (dom_quit.classList.contains("disabled")) return;
        sendMessage("quit", null);
    });

    // Watching Folder

    var clickFile = function(file) {
        if (selected_file) selected_file.element.style.fontWeight = "normal";
        if (file === selected_file) {
            selected_file = null;
            disableFileOptions();
            updateRecordList(null);
        } else {
            file.element.style.fontWeight = "bold";
            selected_file = file;
            enableFileOptions(file);
            updateRecordList(file);
        }
    }

    var clickRecord = function(record) {
        if (selected_record) selected_record.element.style.fontWeight = "normal";
        if (record === selected_record) {
            selected_record = null;
            disableRecordOptions();
        } else {
            record.element.style.fontWeight = "bold";
            selected_record = record;
            enableRecordOptions();
        }
    }

    dom_file_restore.addEventListener("click", function() {
        if (dom_file_restore.classList.contains("disabled")) return;
        sendMessage("file_restore", selected_file);
    });

    dom_file_discard_all.addEventListener("click", function() {
        if (dom_file_discard_all.classList.contains("disabled")) return;
        sendMessage("file_discard_all", selected_file);
    });

    dom_file_clean.addEventListener("click", function() {
        if (dom_file_clean.classList.contains("disabled")) return;
        sendMessage("file_clean", selected_file);
    });

    dom_record_restore.addEventListener("click", function() {
        if (dom_record_restore.classList.contains("disabled")) return;
        sendMessage("record_restore", selected_record);
    });

    dom_record_discard.addEventListener("click", function() {
        if (dom_record_discard.classList.contains("disabled")) return;
        sendMessage("record_discard", selected_record);
    });

    dom_restore_all.addEventListener("click", function() {
        if (dom_restore_all.classList.contains("disabled")) return;
        sendMessage("restore_all", watching_folder);
    });

    dom_discard_all.addEventListener("click", function() {
        if (dom_discard_all.classList.contains("disabled")) return;
        sendMessage("discard_all", watching_folder);
    });

    dom_clean_inactive.addEventListener("click", function() {
        if (dom_clean_inactive.classList.contains("disabled")) return;
        sendMessage("clean_inactive", watching_folder);
    });

    dom_stop_watching.addEventListener("click", function() {
        if (dom_stop_watching.classList.contains("disabled")) return;
        sendMessage("stop_watching", watching_folder);
    });

    // Settings

    var clickSettings = function() {
        sendMessage("settings_change", {
            autoSyncing: auto_syncing,
            launchOnStart: launch_on_start,
            autoDiscardOutdated: auto_discard_outdated,
            autoCleanInactive: auto_clean_inactive
        });
    }








    /* Communication */

    var onOpen = function() {
        console.log("Connected to Websocket Server");
        sendMessage("check_status", null);
    }

    var onMessage = function(event) {
        console.log("Message Received: " + event.data);
        var obj = JSON.parse(event.data);
        switch(obj.type) {
            case "reply":
                switch(obj.content.type) {
                    case "check_status":
                        if (obj.content.reply) 
                            updateOverview(obj.content.archive_folder);
                        break;
                    case "initialize_archive_path":
                        
                        break;
                    case "settings_change":

                        break;
                    case "add_watching_folder":

                        break;
                    case "pause_syncing":
                        setPaused();
                        break;
                    case "resume_syncing":
                        setResumed();
                        break
                    case "check_watching_folder":
                        switchToWatchingFolderPage();
                    case "recheck_file_list":
                        if (obj.content.reply) {
                            files = obj.content.reply.files;
                            updateFilesList();
                        }
                        break;
                    case "stop_watching":
                        location.reload();
                }
                break;
            case "agent_initialized":
                updateOverview(obj.content.archive_folder)
                break;
            case "update_status":
                updateStatus(obj.content);
                break;
            case "update_data":
                if (page === 1) sendMessage("recheck_file_list", watching_folder);
                break;
            case "prev_log":
                for (var i = 0; i < obj.content.prev_server_logs.length; i ++) {
                    dom_server_log_list.lastChild.innerHTML += obj.content.prev_server_logs[i];
                    addLogLine(dom_server_log_list);
                }
                for (var i = 0; i < obj.content.prev_agent_logs.length; i ++) {
                    dom_agent_log_list.lastChild.innerHTML += obj.content.prev_agent_logs[i];
                    addLogLine(dom_agent_log_list);
                }
                break;
            case "server_log_print":
                dom_server_log_list.lastChild.innerHTML += obj.content;
                break;
            case "agent_log_print":
                dom_agent_log_list.lastChild.innerHTML += obj.content;
                break;
            case "server_log_println":
                dom_server_log_list.lastChild.innerHTML += obj.content;
                addLogLine(dom_server_log_list);
                break;
            case "agent_log_println":
                dom_agent_log_list.lastChild.innerHTML += obj.content;
                addLogLine(dom_agent_log_list);
                break;
        }
    }

    var sendMessage = function(type, content) {
        var obj = {type: type, content: content};
        wsSocket.send(JSON.stringify(obj));
        console.log("Message Sent: " + JSON.stringify(obj));
    }

    var onClose = function(event) {
        dom_current_status.innerHTML = "Connection Closed";
        console.log("Disconnected from Websocket Server");
    }

    window.ajaxGet("wsPort.json", function(text) {
        wsPort = JSON.parse(text).wsPort;
        wsSocket = new WebSocket("ws:localhost:" + wsPort);
        wsSocket.onopen = onOpen;
        wsSocket.onmessage = onMessage;
        wsSocket.onclose = onClose;
    })

});
