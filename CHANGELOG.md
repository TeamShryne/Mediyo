# Changelog

User-facing notes for each release. The release workflow reads the section for
the version it is publishing and uses it as the release notes, so **a release
cannot ship without them** — it fails instead. Write them for someone using
the app, not for someone reading the git log: what changed, and why they would
care. No commit hashes, no file names.

The technical change list is still generated, but it is collapsed below a
"Technical changes" heading.

## Unreleased

**Quick picks is a shelf of columns now.** It scrolls sideways and snaps from
one column to the next, with the next one peeking in from the right so you
know there is more. Every row has the artwork, the title and an overflow menu,
and the column you are on is the one that stands out. There is a "Play all"
button next to the heading too.

**Quick picks plays new songs, not just old favourites.** It now mixes the
tracks you are most likely to tap next with a shuffled handful of songs the
app found by queueing — and the way it looks for those is deliberately
random, so the same handful never comes back and the shelf never settles into
one artist on repeat.

**The new shelves look like quick picks.** "Because you heard &lt;song&gt;" uses
the same columns of four, with the same snapping and the same overflow menu.

**Home finally tells you *why* it is showing you something.** The shelves now
read "Because you heard <song>", "Because you loved it", "Because you liked
it", "Because you were away" and "Because it's late" — the last one follows
the clock. The "Because you heard" shelves are new songs, found by looking at
the neighbours of your music and branching out from there, so they are
adjacent to what you like without repeating it.

**Quick picks scroll sideways, and never run out.** The row of songs at the top
of Home is now a horizontal shelf instead of a small grid, and it holds far
more than before. It starts with the songs you are most likely to want next,
then fills with songs you have never heard: the app looks for neighbours of
your music, branches off those to look even further out, and mixes the
results so the shelf does not turn into one artist on repeat. Scroll to the
end and it simply keeps going — new songs each time, never the same ones
twice.

**The lyrics keep up when you slow a song down.** The word-by-word highlight
used to run ahead of the music and then jump back to the right place, so a
half-speed song looked like it was stuttering. It now follows the playback
speed, and the same goes for the moment you tap a line to jump there — the
lyrics move with you right away.

**You decide how the lyrics look.** *Settings → Lyrics* has a new appearance
section with a live preview: change the text size, the font weight (anything
from medium to black) and the distance between the lines. The player picks the
new look up straight away.

## 0.8.0

**Reorder your queue by dragging.** The up and down arrows are gone. Press and
hold a track, then drag it where you want it — and if you drag towards the top
of the list, the list scrolls so you can pull a track up past the header.

**Bottom sheets look right.** The status bar now dims along with the screen
behind a sheet, and the back, home and recents buttons sit on the sheet's own
background instead of floating over the rows behind them. Opening one sheet
also closes any other, so you never get two stacked on top of each other.

**Search results are tidier.** Song rows no longer repeat the word "Song" next
to the track length.

**Nightly builds, if you want them.** *Settings → App updates* now has a
channel picker. Stay on Stable, or switch to Nightly to get daily builds of
the development branch. Nightly builds are not tested — use them only if you
are happy to hit a broken build.
