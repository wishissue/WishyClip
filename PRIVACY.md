# Privacy Policy

**Wishy Clip** is an open-source, free 2D animation application. We respect your privacy completely.

## Data Collection
- **No Ads, No Trackers, No Telemetry**: Wishy Clip contains no third-party ad networks, analytics SDKs, or crash reporting telemetry.
- **Local Storage Only**: All your animation projects, layers, frames, and audio clips are stored exclusively on your device: in the project folder you choose (and a working copy in the app's private storage). WishyClip only accesses that one folder. Nothing is ever uploaded to remote servers by WishyClip; if you pick a folder that another app syncs, that app handles the sync.

## Update check (the only network use)
When the app starts it makes one HTTPS request to `api.github.com` to ask for the newest published release of this project. No account, identifier, project, or usage data is sent (GitHub sees the usual connection details such as your IP address, as with any web request). If a newer version exists, the app asks you; nothing is downloaded until you press **Update**, and Android itself asks you to confirm the installation. You can turn the start-up check off in Settings → About. Builds made for stores that handle updates themselves can leave this feature out.

## Android Permissions Used
0. **`INTERNET`** and **`REQUEST_INSTALL_PACKAGES`**: Used only by the update feature above (the check, the download, and handing the downloaded file to Android's installer after you allow it).
1. **`RECORD_AUDIO`**: Used strictly when you choose to record a voiceover for your animation timeline.
2. **`FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_DATA_SYNC`**: Used by the export service to run background video/GIF rendering with a visible progress notification.
3. **`POST_NOTIFICATIONS`**: Used to notify you when your animation export is complete and ready to share.
