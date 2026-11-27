# Changelog

What changed in each version of Fidelis, newest first.




## 3.0.1

- **Fixed Fidelis closing as soon as it opened** in 3.0.0. The release build trimmed away code the first screen needs; it now keeps all of it, and stays a fast release build.

## 3.0.0

- **A real release build.** Fidelis is now built the way apps from a store are: optimised and trimmed, without the debugging extras. Scrolling, animations and starting up are smoother, and the app is smaller. It installs over the version you have, and your sign-ins, mixes and Spotify setup stay.

**Everything new since 2.3**

- **The player grows out of the mini player.** Pull the mini player up and the full player rises under your finger. Let go or flick and it springs open. Pull it down or swipe back and it sinks into the mini player again.
- **Springy, fluid controls.** Buttons, pills and tiles dip when pressed and bounce back, round controls give a light vibration tick, and the back swipe previews where it goes on Android 14 and newer.
- **Headphone skips land on the right song**, even when you press next quickly several times. Previous on headphones restarts the song, like in Spotify.
- **10-band equaliser after a crash.** It no longer gets stuck in 5-band mode.
- **All, Music and Podcasts pills** on the Playlists tab, with new episodes from your shows. Podcast tiles open Fidelis's own show page.
- **Podcasts for you opens instantly** and updates in the background, with clearer groups: your taste first, then the charts.
- **A faster Listen screen and smoother lists.** Each row appears as soon as it's ready, and covers load at the size they're shown.
- **Vinyl for songs without a cover**, everywhere: the player, every song list and the mini player.
- **Crash report** in Profile, under Diagnostics, ready to copy if Fidelis ever closes unexpectedly.

## 2.8.0

- **Crash report** in Profile, under Diagnostics. If Fidelis closes unexpectedly, the details stay on the phone, ready to copy. No cable or terminal needed.

## 2.7.2

- **Quick skips with headphones** no longer replay the same song. Pressing next again within a few seconds now moves on to the following song.

## 2.7.1

- **Fixed a crash** when starting a song or opening the player. The player's opening spring overshoots a touch, which briefly gave its corners a negative size.

## 2.7.0

- **The player grows out of the mini player.** Pull the mini player up and the full player rises out of it under your finger, from a rounded card to full screen. Let go past a quarter, or flick, and it springs open; let go early and it sinks back. Tapping the mini player plays the same animation.
- **And shrinks back into it.** Pulling the player down or swiping back carries it smoothly into the mini player, and you can cancel a back swipe halfway.
- The tab you were on stays put under the player, so closing it is instant and keeps your place.

## 2.6.0

- **Podcasts for you opens instantly.** Your shows, new episodes and suggestions are kept on the phone and show straight away, then update in the background. Episodes and suggestions load side by side, and charts are kept for a few hours.
- **Clearer podcast groups.** Your own taste comes first, with up to three Your vibe genres, then the charts. New episodes puts ones you've started first and finished ones last.
- **Faster Listen screen.** Quick picks, Listen again and Heavy rotation each appear as soon as they're ready.
- **Smoother scrolling.** Covers in lists load at the size they're shown instead of full size, and stay cached on the phone.

## 2.5.0

- **Drag the mini player up** into the full player. It follows your finger and opens on a soft spring; a flick works too.
- **Dragging the player down** shrinks it a little as it goes, and it springs back at your speed if you let go early.
- **Bounce on controls.** Buttons, pills and tiles dip when pressed and bounce back, even on a quick tap. Round controls give a light vibration tick.
- **Back gesture with a preview** on Android 14 and newer. Pages, the player and bottom sheets follow your back swipe, and you can cancel halfway.

## 2.4.0

- **All, Music and Podcasts pills** on the Playlists tab, like Spotify's home. Music hides podcasts from your mixes, recents and shelves. Podcasts shows new episodes from the shows you follow, podcasts from your Spotify home, and all your shows.
- Podcast tiles on the Playlists tab open Fidelis's own show page.

## 2.3.3

- **Headphone skip plays the right song.** Spotify now holds your next song from the moment each one starts, so the skip button on your headphones goes straight to it, even when you press it quickly several times. It no longer restarts the same song.
- **Previous on headphones** restarts the song, like in Spotify, instead of sometimes skipping ahead.
- **10 bands after a crash.** The equaliser no longer gets stuck in 5-band mode after the app restarts. It tries the full 10 bands again as soon as Spotify is ready.

## 2.3.2

- **Vinyl in the mini player, for real.** The swipeable mini player above the tabs now shows the vinyl for songs without a cover.

## 2.3.1

- **Plain vinyl cover.** The record for songs without art no longer has the song name on its label.
- **Vinyl in lists and the mini player.** Songs without a cover show the vinyl in every song list and in the mini player, instead of an empty square.

## 2.3.0

- **Playlist search.** Search now finds playlists from Spotify and anyone else. Tap one to play it, or use the menu to pin it to Your playlists, see the owner's profile in Spotify, or open it in Spotify. Other people's playlists play whole inside Spotify, because Spotify only lets apps list the songs of your own.
- **Paste a Spotify link.** A playlist link shows the playlist with Play and Pin. A profile link opens that profile in the Spotify app. Spotify stopped letting apps search people in 2026, so this is the way to reach someone's playlists.

## 2.2.0

- **The queue is a sheet.** It slides up from the bottom and swipes away by its handle: up to five songs you've played, the one playing now, then what's next.
- **Vinyl cover, for real.** Songs without art no longer keep the last song's cover.
- **Fixed odd characters** such as "Â" after Up next and in the lyrics.
## 2.1.0

- **Vinyl cover for songs without art.** A record with a blank label and the song's name on it, on Now Playing and in Up next.
- **Scrolling titles.** On Now Playing, a title or artist that doesn't fit scrolls and loops, like Spotify.
- **Smart shuffle.** The new button by Shuffle on playlist pages adds songs that fit the playlist, about one after every three of yours, marked as suggested in Up next.
## 2.0.1

- **Sharp covers.** Now Playing asks Spotify for the largest cover, and every Spotify or Apple Podcasts thumbnail link is swapped for its full-size version.
- **Fewer crashes when switching songs.** The system media card gets a smaller copy of the cover, the notification only updates when something visible changes, and the playback service is no longer restarted from the background.
## 2.0.0

- **Your own Spotify app.** Spotify only lets an app's listed accounts sign in, so Fidelis now signs in through a Spotify app you make. Profile → **Your Spotify app** walks you through it and shows the redirect URI, package and SHA-1 to copy. Builds with a Client ID built in keep using it until you paste another.

## 1.8.0

- **A new Mood tab: the mood dial.** One colour field, calm to hyped and dark to bright. Drag the dot anywhere, choose Just mine, A few new or Mostly new, and make the mix. Songs are scored against the three nearest feelings, so every spot gives its own blend. A new feeling, Intense, covers the heavy, dark corner.
## 1.7.0

- **More accurate lyrics.** LRCLIB, NetEase Cloud Music and Kugou are asked at once, and only versions whose title, artist and length fit the recording playing are kept. Timing that fits no source is dropped in favour of plain words.
- **Word by word.** When NetEase has word timing, each word lights up as it's sung.
- **Timing nudge and source switch.** Move lyrics earlier or later by a quarter second, or tap the source to try another. Both are remembered per song.

## 1.6.1

- **Podcast search works again.** Spotify stopped accepting the request for shows in your account's country, so Fidelis now passes your country directly. When Spotify finds nothing, Apple Podcasts' search fills in.
- **Better podcast picks.** "Because you listen to", "About the artists you play", "Your vibe" and charts for your country and the world, from Apple Podcasts' public charts and search. Tap any of them to open it on Spotify.

## 1.6.0

- **Search any podcast.** A search field on Podcasts finds any show on Spotify, by name, host or topic.
- **Podcasts for you.** "Because you listen to", "From the people behind" and "More in your vibe" rows, plus genre chips with your own genres first. Genres come from Apple Podcasts' public search and are kept on the phone.
- **A clearer radio globe.** The continents are drawn under the station dots, and a circle in the middle lists the stations in and around it, nearest first. Drag, pinch or tap anywhere to move it. The world map is Natural Earth's, which is public domain.

## 1.5.0

- **Save the song on air.** When a station shows what's playing, tap the search button to find it on Spotify, then like it, add it to a playlist or play it.
- **Home screen widget.** The song or station on now, on its own colour from the cover, with play, pause and skip.
- **Radio globe.** A spinning Earth with a dot for every place that has radio, like Radio Garden. Drag, pinch, tap a dot to tune in, or shuffle to land somewhere new.
