package com.gmusic.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.gmusic.app.MainActivity
import com.gmusic.app.R
import com.gmusic.app.domain.model.Track

object GMusicWidgetStateKeys {
    val TITLE = stringPreferencesKey("widget_title")
    val ARTIST = stringPreferencesKey("widget_artist")
    val IS_PLAYING = booleanPreferencesKey("widget_is_playing")
    val IS_LIKED = booleanPreferencesKey("widget_is_liked")
}

class GMusicGlanceWidget : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            SMALL_4X1,
            MEDIUM_4X2
        )
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                WidgetContent(context)
            }
        }
    }

    @Composable
    private fun WidgetContent(context: Context) {
        val prefs = currentState<Preferences>()
        val title = prefs[GMusicWidgetStateKeys.TITLE]
        val artist = prefs[GMusicWidgetStateKeys.ARTIST] ?: ""
        val isPlaying = prefs[GMusicWidgetStateKeys.IS_PLAYING] ?: false
        val isLiked = prefs[GMusicWidgetStateKeys.IS_LIKED] ?: false
        val size = LocalSize.current
        val isMedium = size.height >= 110.dp

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(16.dp)
                .background(ColorProvider(Color(0xFF181818)))
                .clickable(actionStartActivity(openAppIntent))
                .padding(12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (title.isNullOrBlank()) {
                // Stato inattivo: prompt "Apri GMusic" con logo
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        provider = ImageProvider(R.drawable.ic_gmusic_logo),
                        contentDescription = "GMusic",
                        modifier = GlanceModifier.size(36.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(12.dp))
                    Text(
                        text = context.getString(R.string.widget_open_prompt),
                        style = TextStyle(
                            color = ColorProvider(Color(0xFF1B6B3A)),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            provider = ImageProvider(R.drawable.ic_gmusic_logo),
                            contentDescription = "GMusic",
                            modifier = GlanceModifier.size(if (isMedium) 44.dp else 36.dp)
                        )
                        Spacer(modifier = GlanceModifier.width(10.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            Text(
                                text = title,
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = if (isMedium) 17.sp else 15.sp,
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = artist,
                                style = TextStyle(
                                    color = ColorProvider(Color(0xFFB3B3B3)),
                                    fontSize = 13.sp
                                ),
                                maxLines = 1
                            )
                        }
                        if (!isMedium) {
                            WidgetControlsRow(isPlaying = isPlaying, isLiked = isLiked)
                        }
                    }

                    if (isMedium) {
                        Spacer(modifier = GlanceModifier.height(12.dp))
                        WidgetControlsRow(isPlaying = isPlaying, isLiked = isLiked)
                    }
                }
            }
        }
    }

    @Composable
    private fun WidgetControlsRow(isPlaying: Boolean, isLiked: Boolean) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isLiked) "♥" else "♡",
                style = TextStyle(
                    color = ColorProvider(if (isLiked) Color(0xFF1ED760) else Color.White),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(
                        actionRunCallback<WidgetCommandActionCallback>(
                            androidx.glance.action.actionParametersOf(
                                WidgetCommandActionCallback.KEY_CMD to WidgetCommandActionCallback.CMD_LIKE
                            )
                        )
                    )
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "⏮",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 20.sp
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(
                        actionRunCallback<WidgetCommandActionCallback>(
                            androidx.glance.action.actionParametersOf(
                                WidgetCommandActionCallback.KEY_CMD to WidgetCommandActionCallback.CMD_PREV
                            )
                        )
                    )
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = if (isPlaying) "⏸" else "▶",
                style = TextStyle(
                    color = ColorProvider(Color(0xFF1ED760)),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(
                        actionRunCallback<WidgetCommandActionCallback>(
                            androidx.glance.action.actionParametersOf(
                                WidgetCommandActionCallback.KEY_CMD to WidgetCommandActionCallback.CMD_TOGGLE
                            )
                        )
                    )
            )
            Spacer(modifier = GlanceModifier.width(4.dp))
            Text(
                text = "⏭",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 20.sp
                ),
                modifier = GlanceModifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(
                        actionRunCallback<WidgetCommandActionCallback>(
                            androidx.glance.action.actionParametersOf(
                                WidgetCommandActionCallback.KEY_CMD to WidgetCommandActionCallback.CMD_NEXT
                            )
                        )
                    )
            )
        }
    }

    companion object {
        private val SMALL_4X1 = DpSize(250.dp, 64.dp)
        private val MEDIUM_4X2 = DpSize(250.dp, 128.dp)

        suspend fun updateAllWidgets(
            context: Context,
            track: Track?,
            isPlaying: Boolean
        ) {
            try {
                val manager = GlanceAppWidgetManager(context)
                val glanceIds = manager.getGlanceIds(GMusicGlanceWidget::class.java)
                val widget = GMusicGlanceWidget()
                for (glanceId in glanceIds) {
                    updateAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId) { prefs ->
                        prefs.toMutablePreferences().apply {
                            if (track == null) {
                                remove(GMusicWidgetStateKeys.TITLE)
                                remove(GMusicWidgetStateKeys.ARTIST)
                                this[GMusicWidgetStateKeys.IS_PLAYING] = false
                                this[GMusicWidgetStateKeys.IS_LIKED] = false
                            } else {
                                this[GMusicWidgetStateKeys.TITLE] = track.title
                                this[GMusicWidgetStateKeys.ARTIST] = track.artistName
                                this[GMusicWidgetStateKeys.IS_PLAYING] = isPlaying
                                this[GMusicWidgetStateKeys.IS_LIKED] = track.isLiked
                            }
                        }
                    }
                    widget.update(context, glanceId)
                }
            } catch (_: Exception) {
            }
        }
    }
}

class WidgetCommandActionCallback : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val cmd = parameters[KEY_CMD] ?: return
        val intent = Intent(ACTION_WIDGET_MEDIA_COMMAND).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_WIDGET_COMMAND, cmd)
        }
        context.sendBroadcast(intent)
    }

    companion object {
        const val ACTION_WIDGET_MEDIA_COMMAND = "com.gmusic.app.ACTION_WIDGET_MEDIA_COMMAND"
        const val EXTRA_WIDGET_COMMAND = "extra_widget_command"
        val KEY_CMD = ActionParameters.Key<String>("widget_cmd")
        const val CMD_TOGGLE = "toggle"
        const val CMD_NEXT = "next"
        const val CMD_PREV = "prev"
        const val CMD_LIKE = "like"
    }
}

class GMusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GMusicGlanceWidget()
}
