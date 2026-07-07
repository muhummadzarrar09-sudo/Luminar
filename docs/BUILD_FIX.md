# Build Fix — Delete Conflicting Files

The build fails because your local repo has 3 files that were NOT in the original GitHub repo and conflict with the new EPUB/search implementation.

## Delete these 3 files:

```powershell
# Run from your Luminar project root in PowerShell:
Remove-Item "app\src\main\java\com\luminar\reader\data\epub\EpubBookLoader.kt" -Force
Remove-Item "app\src\main\java\com\luminar\reader\presentation\reader\EpubReaderViewModel.kt" -Force
Remove-Item "app\src\main\java\com\luminar\reader\presentation\search\SearchViewModel.kt" -Force
```

## Why?

| File to delete | Problem |
|---|---|
| `EpubBookLoader.kt` | Uses Readium library (not in dependencies). Has a duplicate `EpubMetadata` class that conflicts with `EpubParser.kt`. |
| `EpubReaderViewModel.kt` | References Readium's `Publication` class. Type mismatches with `FontScale`. |
| `SearchViewModel.kt` | References non-existent `indexingProgress`. Search is handled inside `ReaderViewModel` now. |

## What replaces them?

| Deleted file | Replacement | Approach |
|---|---|---|
| `EpubBookLoader.kt` | `data/epub/EpubParser.kt` | Pure JDK `ZipFile` — no external library needed |
| `EpubReaderViewModel.kt` | EPUB logic inside `ReaderViewModel.kt` | Unified reader VM handles PDF + text + EPUB |
| `SearchViewModel.kt` | Search logic inside `ReaderViewModel.kt` | Search state + events built into existing VM |

After deleting, rebuild:
```powershell
.\luminar-run.ps1
```
