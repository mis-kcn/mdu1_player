package tv.mdu1.mdu1_player

import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory
import io.flutter.view.TextureRegistry

class Mdu1ViewFactory(private val messenger: BinaryMessenger, private val textureRegistry: TextureRegistry):PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context?, viewId: Int, args: Any?): PlatformView {
        val params = args as HashMap<*,*>
        return Mdu1View(context!!, messenger, viewId, textureRegistry)
    }
}