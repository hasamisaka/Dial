Dial 1.8.6

### Player
- When a song has a full-screen Canvas, the Canvas continues to play behind the lyrics and the queue. The Canvas is dark and blurred, thus you can read the text. The album art does not become small. The album picture and the song details come into view at the top. Songs without a Canvas do not change.
- Stats for nerds: the codec line shows the YouTube bitrate also when the song plays from the cache.
- Stats for nerds: the codec line does not flash when the song changes. The old line stays until the new line is ready.

### Accounts
- Settings, Account: a badge after the account name shows the plan of the account. A gold "Premium" badge shows for a Premium account. A grey "Free" badge shows for a free account. No badge shows when Dial cannot find the plan. The YouTube Music account, the Spotify account and each profile in the profile list show the badge.
- Dial finds the plan when the app starts and when you sign in. Dial keeps the result on the device.
- The first section of Account and integrations has the new name "YouTube Music".

### High quality audio
- New setting: Settings, Account, YouTube Music, High quality audio. With a YouTube Music Premium account, Dial plays the 256 kbps Opus audio. Before this change, Dial played 160 kbps.
- This setting is on by default for a Premium account. For a free account, the setting is off and you cannot change it.

### Hide AI music
- New setting: Settings, Miscellaneous, Hide AI music. This setting is off by default. When it is on, Dial removes songs and artists of known AI music acts from search, Home, the dials, Explore, mixes, radio and autoplay. This applies also to Spotify search and Spotify radio.
- Dial finds AI music from public lists of AI artists (Soul Over AI, Spotify AI Blocker, aimusic-blocklist). Dial gets new lists one time each day, and only when the setting is on.
- Caution: this filter is not 100% effective. YouTube Music and Spotify do not mark AI songs. Thus, Dial can find only the artists that are on the lists. Some AI songs can still show.
- Your library, your playlists, your history and the files on your device do not change.

### Spotify
- New lyrics source "Spotify". When you sign in to Spotify, Dial gets the lyrics from Spotify with your account. "PaxSenix: Spotify" stays as the second source.
- New setting for the speed dial when its source is Spotify: "Spotify picks from". Select "Plays in Dial" (the default, as before) or "Spotify top". "Spotify top" uses the top songs and artists of your Spotify account. Each pull to refresh uses the next songs and artists.
- Spotify radio works again when the Spotify station is empty. Spotify closed the old route. Dial now uses the "inspired by" mix of the song.
- Dial now finds the current Spotify web player data for all Spotify features (library, playlists, search, Home mixes, Canvas, likes and playlist import). Thus, Spotify features continue to work when Spotify changes its web player.
- Some Spotify routes come from SpotAPI by Aran404 (GPL-3.0).
