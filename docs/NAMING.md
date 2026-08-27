# Naming

**Decision: `Recto`** — package id `dev.recto.reader`.
Fallback if you dislike it: `Deckle` (`dev.deckle.reader`).

---

## Why not keep "Luminar"

Two problems, one of them serious.

**1. Skylum Luminar.** *Luminar Neo* is a well-known commercial AI photo editor sold by
Skylum, on the Mac App Store, Microsoft Store, Windows and macOS. It is an actively
marketed product with a real trademark position in consumer software. Two more
collisions in adjacent space: **Luminar Technologies** (automotive lidar, NASDAQ: LAZR)
and **Luminar Neo**'s own mobile app.

This doesn't matter at all if the app lives on your phone and a few friends' phones.
It matters the moment you put it on Google Play — trademark takedowns on Play are
automated, fast, and unappealable in practice. Since you said "my phone or other
Androids if anyone asks," you're right at the boundary where a rename is cheap now and
expensive later.

**2. It doesn't mean anything book-related.** "Luminar" says light, lens, photography.
Nothing about reading. Worse, the *previous* app carried that name — a fresh name is a
clean psychological break from a codebase whose own audit doc was titled
*"Birth Defects."*

## Candidates considered

Every one was checked against Google Play, GitHub and the general software landscape.

| Name | Meaning | Verdict |
|---|---|---|
| **Recto** ✅ | The right-hand page of an open book (its partner is *verso*). Bookbinding term, centuries old. | **Chosen.** No software collision found. Short, two syllables, typeable, nerdy-but-pronounceable. `dev.recto.reader` is clean. Latin *rectus* = "right/proper" is a quiet bonus. |
| **Deckle** | The rough, untrimmed edge of handmade paper — the fuzzy edge on fine books | Strong runner-up, lovely and tactile. Collision: **Deckle Studio**, a desktop book-*writing* app (deckle.studio), and **usedeckle.com**, a book-formatting service. Adjacent enough to cause confusion, but neither is a reader, and neither is on Android. |
| **Colophon** | The publisher's note at the end of a book: who set it, in what type, on what paper | Beautiful, perfectly on-theme, very nerdy. Taken by an Obsidian writing plugin. Also three syllables and people misspell it. |
| **Quire** | 24 sheets of paper; a gathering of folded leaves that gets bound into a book | Excellent word, but **quire.io** is an established project-management SaaS. Dead on arrival. |
| **Lectern** | The stand you rest an open book on | **Directly taken by two Android apps**, one of which (`nl.hofstack.lectern`) is a *shipping EPUB/PDF reader with TTS, OPDS, streaks and a vocabulary trainer* — i.e. almost exactly this app. Absolutely not. |
| **Marginalia** | Notes scribbled in a book's margins | Taken: an AI-companion desktop ebook reader on GitHub, plus marginalia.nu the search engine. |
| **Verso** | The left-hand page | Fine, but "Verso Books" is a well-known publisher. *Recto* is the same idea without the publisher. |
| **Tsundoku** | Japanese: buying books and letting them pile up unread | Delightful, and painfully accurate for most of us. Taken twice on Play. Also hard to spell for non-Japanese speakers. |
| **Codex** | A bound book, as opposed to a scroll | Massively overused in software. Dozens of collisions. |
| **Ex Libris / Folio / Incunabula / Palimpsest** | — | Folio and Codex are crowded; Incunabula and Palimpsest are unpronounceable at a party. |

## Why Recto wins

- **On-theme without being cute.** It's the actual word for the page you're looking at.
- **Genuinely nerdy** — a bibliophile will smile; everyone else just hears a short word.
- **Uncontested** in software, unlike every other good bookbinding term.
- **Short.** Six letters, fits a launcher label with no truncation, no ambiguity.
- **Room to grow.** If a desktop companion ever happens, *Verso* is sitting right there.

## Identity

- **App name:** Recto
- **Tagline:** *The page you're on.*
- **Package:** `dev.recto.reader`
- **Icon direction:** an open book abstracted to two rectangles, the right-hand one
  (the recto) picked out in the accent colour. Reads at 48 dp.

## If you'd rather keep Luminar

Entirely defensible for a personal sideloaded app — nobody sends a cease-and-desist over
an APK shared with friends. Say the word and I'll use `com.luminar.reader`. The only
thing you'd be giving up is a painless path to Play Store later, because **changing the
package id after publishing is impossible** — it's a new listing and every existing
install is orphaned.

Everything in the codebase will reference the package id through the Gradle version
catalog and a single `namespace` per module, so a rename before Phase 0 ships costs
about five minutes. After the first release, it costs everything.
