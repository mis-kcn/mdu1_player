package tv.mdu1.mdu1_player

import android.annotation.SuppressLint
import android.net.Uri
import com.google.android.exoplayer2.Format
import com.google.android.exoplayer2.analytics.PlayerId
import com.google.android.exoplayer2.extractor.Extractor
import com.google.android.exoplayer2.extractor.ExtractorInput
import com.google.android.exoplayer2.extractor.mp3.Mp3Extractor
import com.google.android.exoplayer2.extractor.mp4.FragmentedMp4Extractor
import com.google.android.exoplayer2.extractor.ts.*
import com.google.android.exoplayer2.source.hls.*
import com.google.android.exoplayer2.util.Assertions
import com.google.android.exoplayer2.util.FileTypes
import com.google.android.exoplayer2.util.MimeTypes
import com.google.android.exoplayer2.util.TimestampAdjuster
import com.google.common.primitives.Ints
import java.io.EOFException
import java.io.IOException

 class CustomHlsExtractorFactory(val myCallback: (data: Boolean) -> Unit) : HlsExtractorFactory  {
    private val DEFAULT_EXTRACTOR_ORDER = intArrayOf(
        FileTypes.MP4,
        FileTypes.WEBVTT,
        FileTypes.TS,
        FileTypes.ADTS,
        FileTypes.AC3,
        FileTypes.AC4,
        FileTypes.MP3
    )

    private var payloadReaderFactoryFlags = DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM

     private fun addFileTypeIfValidAndNotPresent(
        fileType: @FileTypes.Type Int, fileTypes: MutableList<Int>
    ) {
        if (Ints.indexOf(DEFAULT_EXTRACTOR_ORDER, fileType) == -1 || fileTypes.contains(fileType)) {
            return
        }
        fileTypes.add(fileType)
    }

    @SuppressLint("SwitchIntDef") // HLS only supports a small subset of the defined file types.
    fun createExtractorByFileType(
        fileType: @FileTypes.Type Int,
        format: Format,
        muxedCaptionFormats: List<Format>?,
        timestampAdjuster: TimestampAdjuster
    ): Extractor? {
        return when (fileType) {
            FileTypes.WEBVTT -> WebvttExtractor(format.language, timestampAdjuster)
            FileTypes.ADTS -> AdtsExtractor()
            FileTypes.AC3 -> Ac3Extractor()
            FileTypes.AC4 -> Ac4Extractor()
            FileTypes.MP3 -> Mp3Extractor( /* flags= */0,  /* forcedFirstSampleTimestampUs= */0)
            FileTypes.MP4 -> createFragmentedMp4Extractor(
                timestampAdjuster,
                format,
                muxedCaptionFormats
            )
            FileTypes.TS -> createTsExtractor(
                payloadReaderFactoryFlags,
//                exposeCea608WhenMissingDeclarations,
//                format,
                muxedCaptionFormats,
                timestampAdjuster
            )
            else -> null
        }
    }

    private fun createTsExtractor(
        userProvidedPayloadReaderFactoryFlags: @DefaultTsPayloadReaderFactory.Flags Int,
//        exposeCea608WhenMissingDeclarations: Boolean,
//        format: Format,
        muxedCaptionFormats: List<Format>?,
        timestampAdjuster: TimestampAdjuster
    ): TsExtractor? {
        var muxedCaptionFormats = muxedCaptionFormats

        var payloadReaderFactoryFlags =
            (DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                    or userProvidedPayloadReaderFactoryFlags)
        val sampleCea = Format.Builder().setSampleMimeType(MimeTypes.APPLICATION_CEA608).setLanguage("eng").build()
        val sampleCeaList = listOf(sampleCea)

        if (muxedCaptionFormats != null && muxedCaptionFormats.isNotEmpty()) {
            payloadReaderFactoryFlags =
                payloadReaderFactoryFlags or DefaultTsPayloadReaderFactory.FLAG_OVERRIDE_CAPTION_DESCRIPTORS
            this.myCallback.invoke(false)
        } else {
            muxedCaptionFormats = sampleCeaList
            this.myCallback.invoke(true)
        }


        return TsExtractor(
            TsExtractor.MODE_HLS,
            timestampAdjuster,
            DefaultTsPayloadReaderFactory(payloadReaderFactoryFlags, muxedCaptionFormats)
        )
    }

    private fun createFragmentedMp4Extractor(
        timestampAdjuster: TimestampAdjuster,
        format: Format,
        muxedCaptionFormats: List<Format>?
    ): FragmentedMp4Extractor? {
        // Only enable the EMSG TrackOutput if this is the 'variant' track (i.e. the main one) to avoid
        // creating a separate EMSG track for every audio track in a video stream.
        return FragmentedMp4Extractor( /* flags= */
            if (isFmp4Variant(format)) FragmentedMp4Extractor.FLAG_ENABLE_EMSG_TRACK else 0,
            timestampAdjuster,  /* sideloadedTrack= */
            null,
            muxedCaptionFormats ?: emptyList()
        )
    }

    /** Returns true if this `format` represents a 'variant' track (i.e. the main one).  */
    private fun isFmp4Variant(format: Format): Boolean {
        val metadata = format.metadata ?: return false
        for (i in 0 until metadata.length()) {
            val entry = metadata[i]
            if (entry is HlsTrackMetadataEntry) {
                return !entry.variantInfos.isEmpty()
            }
        }
        return false
    }

    @Throws(IOException::class)
    private fun sniffQuietly(extractor: Extractor, input: ExtractorInput): Boolean {
        var result = false
        try {
            result = extractor.sniff(input)
        } catch (e: EOFException) {
            // Do nothing.
        } finally {
            input.resetPeekPosition()
        }
        return result
    }

     override fun createExtractor(
         uri: Uri,
         format: Format,
         muxedCaptionFormats: MutableList<Format>?,
         timestampAdjuster: TimestampAdjuster,
         responseHeaders: MutableMap<String, MutableList<String>>,
         sniffingExtractorInput: ExtractorInput,
         playerId: PlayerId
     ): HlsMediaChunkExtractor {
         val formatInferredFileType = FileTypes.inferFileTypeFromMimeType(format.sampleMimeType)
         val responseHeadersInferredFileType =
             FileTypes.inferFileTypeFromResponseHeaders(responseHeaders)
         val uriInferredFileType = FileTypes.inferFileTypeFromUri(uri!!)

         // Defines the order in which to try the extractors.
         val fileTypeOrder: MutableList<Int> =
             ArrayList( /* initialCapacity= */DEFAULT_EXTRACTOR_ORDER.size)
         addFileTypeIfValidAndNotPresent(formatInferredFileType, fileTypeOrder)
         addFileTypeIfValidAndNotPresent(responseHeadersInferredFileType, fileTypeOrder)
         addFileTypeIfValidAndNotPresent(uriInferredFileType, fileTypeOrder)
         for (fileType in DEFAULT_EXTRACTOR_ORDER) {
             addFileTypeIfValidAndNotPresent(fileType, fileTypeOrder)
         }

         // Extractor to be used if the type is not recognized.
         var fallBackExtractor: Extractor? = null
         sniffingExtractorInput.resetPeekPosition()
         for (i in fileTypeOrder.indices) {
             val fileType = fileTypeOrder[i]
             val extractor = Assertions.checkNotNull(
                 createExtractorByFileType(fileType, format, muxedCaptionFormats, timestampAdjuster)
             )
             if (sniffQuietly(extractor, sniffingExtractorInput)) {
                 return BundledHlsMediaChunkExtractor(extractor, format, timestampAdjuster)
             }
             if (fallBackExtractor == null
                 && (fileType == formatInferredFileType || fileType == responseHeadersInferredFileType || fileType == uriInferredFileType || fileType == FileTypes.TS)
             ) {
                 // If sniffing fails, fallback to the file types inferred from context. If all else fails,
                 // fallback to Transport Stream. See https://github.com/google/ExoPlayer/issues/8219.
                 fallBackExtractor = extractor
             }
         }
         return BundledHlsMediaChunkExtractor(
             Assertions.checkNotNull(fallBackExtractor), format, timestampAdjuster
         )
     }
 }