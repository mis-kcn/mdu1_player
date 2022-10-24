package tv.mdu1.mdu1_player

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.exoplayer2.C
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import io.flutter.view.TextureRegistry

class Mdu1View(context: Context, creationParams: Map<String?, Any?>?, messenger: BinaryMessenger, id: Int, textureRegistry: TextureRegistry):PlatformView, MethodChannel.MethodCallHandler {
    private val methodChannel: MethodChannel = MethodChannel(messenger, "mdu1_player_$id")
    lateinit var activityLifecycleCallbacks: Application.ActivityLifecycleCallbacks
    private var videoView: Mdu1UIView
    private var eventChannel: EventChannel
    private var captionsEnabled: Boolean = false

    init {
        captionsEnabled = creationParams?.get("captions").toString().toBoolean()
        methodChannel.setMethodCallHandler(this)
        eventChannel = EventChannel(messenger, "mdu1_player/video_events")
        videoView = Mdu1UIView(context, textureRegistry, eventChannel)
        val layout = ViewGroup.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
        videoView.layoutParams = layout
        setupLifeCycle(context)
    }

    override fun getView(): View {
        return videoView
    }

    override fun dispose() {
        videoView.releasePlayer()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "init" -> {
                val url: String? = call.argument("url")
                videoView.initializePlayer(url!!, captionsEnabled)
            }
            "updateChannel" -> {
                val url: String? = call.argument("url")
                videoView.updateChannel(url!!)
            }
            "resume" -> {
                onStart()
            }
            "pause" -> {
                onPause()
            }
            "dispose" -> {
                dispose()
            }
            "play" -> {
                videoView.play()
            }
            "stop" -> {
                videoView.stop()
            }
            "reset" -> {
                videoView.reset()
            }
            "jumpTo" -> {
                val seekTime: Double? = call.argument("millisecond")
                seekTime?.let {
                    videoView.jumpTo(it)
                }
            }
            "seekTo" -> {
                val seekTime: Double? = call.argument("millisecond")
                seekTime?.let {
                    videoView.seekTo(it)
                }
            }
            "playing" -> {
                result.success(videoView.getPlaying())
            }
            "getVideoTracks" -> {
                result.success(videoView.getTracks(C.TRACK_TYPE_VIDEO))
            }
            "getAudioTracks" -> {
                result.success(videoView.getTracks(C.TRACK_TYPE_AUDIO))
            }
            "getCaptions" -> {
                result.success(videoView.getTracks(C.TRACK_TYPE_TEXT))
            }
            "setAudioTrack" -> {
                val trackGroupIndex: Int? = call.argument("trackGroupIndex")
                val trackIndex: Int? = call.argument("trackIndex")
                result.success(videoView.setTrack(C.TRACK_TYPE_AUDIO, trackGroupIndex!!, trackIndex!!))
            }
            "setVideoTrack" -> {
                val trackGroupIndex: Int? = call.argument("trackGroupIndex")
                val trackIndex: Int? = call.argument("trackIndex")
                result.success(videoView.setTrack(C.TRACK_TYPE_VIDEO, trackGroupIndex!!, trackIndex!!))
            }
            "setCaptionTrack" -> {
                val trackGroupIndex: Int? = call.argument("trackGroupIndex")
                val trackIndex: Int? = call.argument("trackIndex")
                result.success(videoView.setTrack(C.TRACK_TYPE_TEXT, trackGroupIndex!!, trackIndex!!))
            }
            "currentPosition" -> {
                result.success(videoView.getCurrentPosition())
            }
            "duration" -> {
                result.success(videoView.getDuration())
            }
            "exitApp" -> {
                android.os.Process.killProcess(android.os.Process.myPid())
            }
            else -> {
            }
        }
    }

    private fun setupLifeCycle(context: Context) {
        activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // no-op
            }

            override fun onActivityStarted(activity: Activity) {
                onStart()
            }

            override fun onActivityResumed(activity: Activity) {
                onStart()
            }

            override fun onActivityPaused(activity: Activity) {
                onPause()
            }

            override fun onActivityStopped(activity: Activity) {
                onStop()
            }

            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                // no-op
            }

            override fun onActivityDestroyed(activity: Activity) {
                onDestroy()
            }
        }

        (context.applicationContext as Application).registerActivityLifecycleCallbacks(this.activityLifecycleCallbacks)
    }

    private fun onStart() {
        videoView.onStart(captionsEnabled)
    }

    private fun onStop() {
        videoView.onStop()
    }

    private fun onPause() {
        videoView.onPause()
    }

    private fun onDestroy() {
        videoView.onDestroy()
    }
}