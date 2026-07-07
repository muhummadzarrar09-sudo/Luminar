# Luminar Reader — Format Audit: What We Have vs What We're Missing

## Research sources
- KOReader (gold standard e-reader): EPUB, PDF, DjVu, XPS, CBT, CBZ, FB2, PDB, TXT, HTML, RTF, CHM, DOC, MOBI, ZIP
- ReadEra: EPUB, PDF, DOC, DOCX, RTF, TXT, DJVU, FB2, MOBI, CHM
- Kindle: AZW, AZW3, KFX, EPUB, PDF, DOCX, DOC, RTF, TXT, HTML, MOBI
- Librera Reader: PDF, EPUB, MOBI, DjVu, FB2, TXT, RTF, AZW, AZW3, HTML, CBZ, CBR
- File Viewer Plus: 400+ formats
- All Document Reader (86M installs): PDF, DOCX, XLSX, PPTX, TXT, EPUB
- Sumatra PDF: PDF, EPUB, MOBI, XPS, DjVu, CBZ, CBR

---

## ✅ What Luminar ALREADY supports (23 formats)

| Category | Formats |
|----------|---------|
| PDF | PDF |
| E-books | EPUB |
| Office (modern) | DOCX, XLSX, PPTX |
| Office (legacy) | DOC, XLS, PPT (best-effort) |
| OpenDocument | ODT, ODS, ODP |
| Rich text | RTF |
| Fiction | FB2 |
| Markdown | MD |
| Plain text | TXT, LOG |
| Web | HTML |
| Data | JSON, XML, CSV |
| Code | 50+ extensions |
| Placeholder | MOBI (message), DjVu (message) |

---

## ❌ What's MISSING to beat every reader on the planet

### Tier 1 — CRITICAL (every competitor has these)
| Format | What it is | Can we do it without build changes? |
|--------|-----------|-------------------------------------|
| **CBZ** | Comic Book ZIP — ZIP of images | ✅ YES — it's just a ZIP of JPG/PNG. Extract + display images |
| **CBR** | Comic Book RAR — RAR of images | ⚠️ PARTIAL — RAR is proprietary. We can detect it and show a "convert to CBZ" message |
| **CHM** | Compiled HTML Help (Microsoft) | ✅ YES — it's a structured binary but we can extract readable text |
| **XPS/OXPS** | XML Paper Specification | ✅ YES — ZIP of XML + images, like DOCX |
| **PDB/PRC** | Palm Database (old e-books) | ✅ YES — simple binary format, text extractable |

### Tier 2 — COMPETITIVE EDGE (beats Kindle & most readers)
| Format | What it is | Can we do it? |
|--------|-----------|---------------|
| **AZW3** | Kindle Format 8 — modified MOBI in a Palm DB container | ⚠️ PARTIAL — DRM-free AZW3 is basically MOBI+HTML. We can extract text from unencrypted ones |
| **MOBI** (actual reading) | Mobipocket — Palm DB + PalmDOC compression | ✅ YES — PDB header + PalmDOC decompress + HTML extract |
| **CBT** | Comic Book TAR | ✅ YES — TAR of images, Android can read TAR |

### Tier 3 — DIFFERENTIATORS (very few apps do these)
| Format | What it is | Can we do it? |
|--------|-----------|---------------|
| **ZIP** (as archive reader) | Open ZIP and show contents/read text inside | ✅ YES — already use ZipFile |

---

## 🐌 Performance issues to address

The "app feels slow" for a 40MB APK likely comes from:

1. **File picker uses `*/*`** — this makes Android scan ALL files which is very slow. Should use specific MIME types
2. **Text content loaded on main thread or large files block UI** — need to ensure proper chunking for big files
3. **LazyColumn recomposition** — search highlighting rebuilds AnnotatedString on every recompose
4. **Regex in parsers** — compiled Regex patterns should be cached, not recreated per call
5. **No loading skeleton** — library shows blank then jumps to content

---

## 📋 Action plan

### What to build NOW:
1. CBZ reader (image-per-page comic viewer)
2. MOBI parser (PalmDOC decompression → HTML → text)
3. CHM parser (ITSS header → extract HTML sections)
4. XPS parser (ZIP → XML pages → text)
5. PDB/PRC parser (Palm DB → text records)
6. CBR/CBT detection + helpful messages
7. AZW3 basic text extraction (DRM-free only)

### Performance fixes:
1. Remove `*/*` from MIME types — use explicit list only
2. Cache compiled Regex patterns in parsers
3. Add loading shimmer/skeleton to library
4. Debounce search highlighting
5. Lazy-load text content with coroutines + progress indicator

### UI refinements:
1. Smoother library animations
2. Better empty states with illustrations
3. Pull-to-refresh in library
4. Haptic feedback on interactions
