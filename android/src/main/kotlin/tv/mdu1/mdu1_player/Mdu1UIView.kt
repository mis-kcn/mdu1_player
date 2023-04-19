package tv.mdu1.mdu1_player

import android.content.Context
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.ext.cronet.CronetDataSource
import com.google.android.exoplayer2.ext.cronet.CronetUtil
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.hls.DefaultHlsDataSourceFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelectionOverride
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.ui.StyledPlayerView.SHOW_BUFFERING_NEVER
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import io.flutter.plugin.common.EventChannel
import io.flutter.view.TextureRegistry
import okhttp3.OkHttpClient
import org.chromium.net.CronetEngine
import java.util.*
import java.util.concurrent.Executors


class Mdu1UIView : FrameLayout, Player.Listener {
    private var textureRegistry: TextureRegistry? = null
    private lateinit var mdu1Player: StyledPlayerView
    private var player: ExoPlayer? = null
    private var videoUrl = ""
    private val trackSelector: DefaultTrackSelector
    private lateinit var bandwidthMeter: DefaultBandwidthMeter
    private val eventSink = QueuingEventSink()
    private lateinit var eventChannel: EventChannel
    private var lastSendBufferedPosition = 0L
    private var isAutoSelected = true
    private var useAutoInTrackName = false
    private val cronetEngine: CronetEngine?
    private val okHttpClient: OkHttpClient

    constructor(context: Context, textureRegistry: TextureRegistry, eventChannel: EventChannel) : super(context) {
        trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory())
        this.textureRegistry = textureRegistry
        this.eventChannel = eventChannel
        this.cronetEngine = CronetUtil.buildCronetEngine(context)
        this.okHttpClient = OkHttpClient.Builder().build()
        init()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory())
        this.cronetEngine = CronetUtil.buildCronetEngine(context)
        this.okHttpClient = OkHttpClient.Builder().build()
        init()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        trackSelector = DefaultTrackSelector(context, AdaptiveTrackSelection.Factory())
        this.cronetEngine = CronetUtil.buildCronetEngine(context)
        this.okHttpClient = OkHttpClient.Builder().build()
        init()
    }

    private fun init() {
        val layout = ViewGroup.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT
        )
        layoutParams = layout
        inflate(context, R.layout.view_player, this)
        mdu1Player = findViewById(R.id.mdu1_player)
        mdu1Player.useController = false
        mdu1Player.hideController()
        mdu1Player.setShowBuffering(SHOW_BUFFERING_NEVER)
        mdu1Player.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT;
        bandwidthMeter = DefaultBandwidthMeter.Builder(context).build()
        
        player = ExoPlayer.Builder(context).setTrackSelector(trackSelector).build()
//        player?.addAnalyticsListener(EventLogger(trackSelector))
       
        eventChannel.setStreamHandler(
            object  : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, sink: EventChannel.EventSink?) {
                    eventSink.setDelegate(sink)
                }

                override fun onCancel(arguments: Any?) {
                    eventSink.setDelegate(null)
                }
            }
        )

        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
//                        val event: MutableMap<String, Any> = HashMap()
//                        event["event"] = "bufferingStart"
//                        eventSink.success(event)

//                            val bufferedPosition = player!!.bufferedPosition
//                            if (bufferedPosition != lastSendBufferedPosition) {
                            val bufferingUpdateEvent: MutableMap<String, Any> = HashMap()
                            bufferingUpdateEvent["event"] = "bufferingUpdate"
                            bufferingUpdateEvent["percentage"] = player?.getBufferedPercentage().toString()
                            eventSink.success(bufferingUpdateEvent)
                            // lastSendBufferedPosition = bufferedPosition
//                            }
                    }
                    Player.STATE_READY -> {
                        val event: MutableMap<String, Any> = HashMap()
                        event["event"] = "bufferingEnd"
                        eventSink.success(event)
                    }
                    Player.STATE_ENDED -> {
                        val event: MutableMap<String, Any?> = HashMap()
                        event["event"] = "completed"
                        eventSink.success(event)
                    }
                    Player.STATE_IDLE -> {
                        //no-op
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player?.seekToDefaultPosition();
                    player?.prepare();
                } else {
                    val event: MutableMap<String, Any?> = HashMap()
                    event["event"] = "exception"
                    event["errorCodeName"] = error.errorCodeName
                    event["error"] = "[${error.errorCode}] ${error.message}"
                    eventSink.success(event)

                    player?.prepare()
                }
            }
        })
    }

    private fun buildDataSourceFactory(context: Context): DataSource.Factory {
        return if(cronetEngine != null) {
            Log.d("tv.mdu1.iptv/player", "using cronet")
            val cronetFactory = CronetDataSource.Factory(cronetEngine, Executors.newCachedThreadPool())
            cronetFactory.setUserAgent(Util.getUserAgent(context, "mdu1_player"))
            cronetFactory.setTransferListener(bandwidthMeter)
            DefaultDataSource.Factory(context, cronetFactory)
        } else {
            Log.d("tv.mdu1.iptv/player", "using okhttp data source")
            val defaultHttpFactory = OkHttpDataSource.Factory(okHttpClient)
            defaultHttpFactory.setUserAgent(Util.getUserAgent(context, "mdu1_player"))
            defaultHttpFactory.setTransferListener(bandwidthMeter)

            DefaultDataSource.Factory(context, defaultHttpFactory)
        }
    }

    private fun buildMediaSource(url: String, dataFactory: DataSource.Factory): MediaSource? {
        val uri = Uri.parse(url)
        val mediaItem = MediaItem.fromUri(uri)
        return when (Util.inferContentType(url)) {
            C.CONTENT_TYPE_HLS -> {
                val customHlsExtractorFactory = CustomHlsExtractorFactory { data ->
                    useAutoInTrackName = data
                }

                HlsMediaSource.Factory(dataFactory).setExtractorFactory(customHlsExtractorFactory).setAllowChunklessPreparation(false).createMediaSource(mediaItem)
            }
            else -> {
                null
            }
        }
    }

    fun releasePlayer() {
        if (player != null) {
            player?.release()
            player = null
        }
    }

    fun initializePlayer(url: String, enableCaptions: Boolean?) {
        videoUrl = url
        val mediaSource = buildMediaSource(videoUrl, buildDataSourceFactory(context))
        mediaSource?.let {
            player?.setMediaSource(it)
            player?.prepare()

            player?.playWhenReady = true
            player?.repeatMode = Player.REPEAT_MODE_OFF
            mdu1Player.player = player
        }
        mdu1Player.onResume()

        if(player != null) {
            if(enableCaptions == false) {
                player!!.trackSelectionParameters = player!!.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .setPreferredTextLanguage("")
                    .build()
            } else if (enableCaptions == true) {
                player!!.trackSelectionParameters = player!!.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage("eng")
                    .build()
            }
        }
    }

    fun getTracks(index: Int): ArrayList<MutableMap<String, Any>> {
        val event: ArrayList<MutableMap<String, Any>> = ArrayList();
        var hasCaptions = false

        player?.currentTracks?.groups?.forEachIndexed {
            trackGroupIndex, it ->
            for (i in 0 until it.length) {
                val trackFormat = it.getTrackFormat(i)

                if(trackFormat.sampleMimeType!!.contains("audio") && index == C.TRACK_TYPE_AUDIO) {
                    val data: MutableMap<String, Any> = HashMap();
                    when (trackFormat.language) {
                        "en" -> {
                            data["name"] =  "English " + (if (trackFormat.channelCount > 1) "Stereo" else "Mono")
                        }
                        "es" -> {
                            data["name"] =  "Spanish " + (if (trackFormat.channelCount > 1) "Stereo" else "Mono")
                        }
                        else -> {
                            var label = trackFormat.label;
                            if(label?.contains("und") == true) {
                                label = label.replace("und ", "Undefined ")
                            } else if (label == null) {
                                label = "";
                            }
                            data["name"] =  label.toString() + " " + (if (trackFormat.channelCount > 1) "Stereo" else "Mono")
                        }
                    }

                    data["isSelected"] = it.isSelected
                    data["trackGroupIndex"] = trackGroupIndex
                    data["trackIndex"] = i
                    event.add(data)
                } else if (trackFormat.sampleMimeType!!.contains("video") && index == C.TRACK_TYPE_VIDEO) {
                    val data: MutableMap<String, Any> = HashMap();
                    data["name"] = trackFormat.width.toString() + " x " + trackFormat.height.toString() + "\t\t" + (trackFormat.bitrate / 1024 / 1024).toString() + " Mbps"
                    if(isAutoSelected) {
                        data["isSelected"] = false
                    } else {
                        data["isSelected"] = it.isTrackSelected(i)
                    }
                    data["trackGroupIndex"] = trackGroupIndex
                    data["trackIndex"] = i
                    event.add(data)
                } else if ((trackFormat.sampleMimeType!!.contains("cea") || trackFormat.sampleMimeType!!.contains("eia")) && index == C.TRACK_TYPE_TEXT) {
                    val data: MutableMap<String, Any> = HashMap();
                    if(useAutoInTrackName) {
                        data["name"] = "Auto"
                    } else {
                        when (trackFormat.language) {
                            "en" -> {
                                data["name"] =  "English"
                            }
                            "es" -> {
                                data["name"] =  "Spanish"
                            }
                            else -> {
                                var label = trackFormat.label;
                                if(label?.contains("und") == true) {
                                    label = label.replace("und ", "Undefined ")
                                } else if (label == null) {
                                    label = "Undefined"
                                }

                                data["name"] = label.toString()
                            }
                        }
                    }
                    data["isSelected"] = it.isSelected
                    data["trackGroupIndex"] = trackGroupIndex
                    data["trackIndex"] = i

                    if(!hasCaptions) {
                        hasCaptions = it.isSelected
                    }
                    event.add(data)
                } else if (trackFormat.sampleMimeType!!.contains("text") && index == C.TRACK_TYPE_TEXT) {
                    val data: MutableMap<String, Any> = HashMap();
                    if(useAutoInTrackName) {
                        data["name"] = "Auto"
                    } else {
                        when (trackFormat.language) {
                            "en" -> {
                                data["name"] =  "English " + trackFormat.label
                            }
                            "es" -> {
                                data["name"] =  "Spanish"
                            }
                            else -> {
                                var label = trackFormat.label;
                                if(label?.contains("und") == true) {
                                    label = label.replace("und ", "Undefined ")
                                } else if (label == null) {
                                    label = "Undefined";
                                }

                                data["name"] = label.toString()
                            }
                        }
                    }
                    data["isSelected"] = it.isSelected
                    data["trackGroupIndex"] = trackGroupIndex
                    data["trackIndex"] = i
                    if(!hasCaptions) {
                        hasCaptions = it.isSelected
                    }
                    // event.add(data)
                }
            }
        }

        if(index == C.TRACK_TYPE_TEXT) {
            val data: MutableMap<String, Any> = HashMap();
            data["name"] = "Off"
            data["isSelected"] = hasCaptions == false
            data["trackGroupIndex"] = -1
            data["trackIndex"] = -1
            event.add(0, data)
        } else if (index == C.TRACK_TYPE_VIDEO) {
            event.reverse()
            val data: MutableMap<String, Any> = HashMap();
            data["name"] = "Auto"
            data["isSelected"] = isAutoSelected == true
            data["trackGroupIndex"] = -1
            data["trackIndex"] = -1
            event.add(0, data)
        }

        return event
    }

    fun setTrack(trackType: Int, trackGroupIndex: Int, trackIndex: Int): Boolean {
        if(trackType == C.TRACK_TYPE_TEXT && trackGroupIndex == -1 && trackIndex == -1) {
            player!!.trackSelectionParameters = player!!.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .setPreferredTextLanguage("")
                .build()

            return true
        }

        if(trackType == C.TRACK_TYPE_VIDEO) {
            if(trackGroupIndex == -1 && trackIndex == -1) {
                isAutoSelected = true
                return true
            } else {
                isAutoSelected = false
            }
        }


        player!!.trackSelectionParameters = player!!.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(
                TrackSelectionOverride(
                    player?.currentTracks?.groups?.elementAt(trackGroupIndex)!!.mediaTrackGroup,
                    trackIndex,
                )
            )
            .build()

        return true
    }

    fun updateChannel(url: String, enableCaptions: Boolean) {
        videoUrl = url
        player?.pause()
        player?.setMediaSource(buildMediaSource(videoUrl, buildDataSourceFactory(context))!!)
        player?.prepare()
        player?.play()

        if(player != null) {
            if(!enableCaptions) {
                player!!.trackSelectionParameters = player!!.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .setPreferredTextLanguage("")
                    .build()
            } else {
                player!!.trackSelectionParameters = player!!.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage("eng")
                    .build()
            }
        }
    }

    fun updateResizeMode(resizeMode: Int) {
        mdu1Player.resizeMode = resizeMode
    }

    fun onStart(enableCaptions: Boolean?) {
        initializePlayer(videoUrl, enableCaptions)
    }

    fun onStop() {
        mdu1Player.onPause()
        releasePlayer()
    }

    fun onPause() {
        player?.pause()
    }

    fun onDestroy() {
        releasePlayer()
    }

    fun play() {
        player?.play()
    }

    fun stop() {
        player?.pause()
    }

    fun reset() {
        releasePlayer()
    }

    fun seekTo(millisecond: Double) {
        val currentPos = player?.currentPosition
        val duration = player?.duration
        currentPos?.let { cur ->
            duration?.let { dur ->
                var seekTime = cur + millisecond
                if (seekTime < 0.0) seekTime = 0.0
                if (seekTime > dur) seekTime = dur.toDouble()
                player?.seekTo(seekTime.toLong())
            }
        }
    }

    fun jumpTo(millisecond: Double) {
        val duration = player?.duration
        duration?.let { dur ->
            var seekTime = millisecond
            if (seekTime < 0.0) seekTime = 0.0
            if (seekTime > dur) seekTime = dur.toDouble()
            player?.seekTo(seekTime.toLong())
        }
    }

    fun getPlaying(): Boolean {
        return player?.isPlaying == true
    }

    fun getCurrentPosition(): Long {
        player?.currentPosition?.let {
            return it
        }
        return 0L
    }

    fun getDuration(): Long {
        player?.duration?.let {
            return it
        }
        return 0L
    }
}