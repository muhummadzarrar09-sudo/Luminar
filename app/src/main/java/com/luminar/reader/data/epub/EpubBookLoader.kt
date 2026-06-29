package com.luminar.reader.data.epub

import android.content.Context
import android.graphics.Bitmap
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.cover
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EpubTocItem(
    val title: String,
    val href: String
)

data class EpubMetadata(
    val title: String?,
    val author: String?,
    val coverPath: String?,
    val toc: List<EpubTocItem> = emptyList()
)

@Singleton
class EpubBookLoader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val httpClient by lazy { DefaultHttpClient() }
    private val assetRetriever by lazy { AssetRetriever(context.contentResolver, httpClient) }
    private val publicationOpener by lazy {
        PublicationOpener(
            DefaultPublicationParser(
                context = context,
                httpClient = httpClient,
                assetRetriever = assetRetriever,
                pdfFactory = null
            )
        )
    }

    suspend fun openPublication(file: File): Publication? = withContext(Dispatchers.IO) {
        runCatching {
            val asset = assetRetriever.retrieve(file).getOrNull() ?: return@runCatching null
            publicationOpener.open(asset, allowUserInteraction = false).getOrNull()
        }.getOrNull()
    }

    suspend fun loadMetadata(file: File): EpubMetadata = withContext(Dispatchers.IO) {
        val publication = openPublication(file) ?: return@withContext EpubMetadata(null, null, null)
        try {
            val title = publication.metadata.title
            val author = publication.metadata.authors.firstOrNull()?.name
            var coverPath: String? = null

            runCatching { publication.cover() }.getOrNull()?.let { bitmap ->
                val coversDir = File(context.filesDir, "covers").apply { mkdirs() }
                val coverFile = File(coversDir, "${UUID.randomUUID()}.png")
                coverFile.outputStream().use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                coverPath = coverFile.absolutePath
            }

            val toc = publication.tableOfContents.map { link ->
                EpubTocItem(title = link.title ?: "Chapter", href = link.href.toString())
            }

            EpubMetadata(title = title, author = author, coverPath = coverPath, toc = toc)
        } finally {
            publication.close()
        }
    }
}
