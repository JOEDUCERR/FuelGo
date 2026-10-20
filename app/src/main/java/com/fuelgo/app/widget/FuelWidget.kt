package com.fuelgo.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fuelgo.app.FuelGoApp
import com.fuelgo.app.MainActivity
import com.fuelgo.app.R
import com.fuelgo.app.data.prefs.WidgetState
import com.fuelgo.app.domain.hoursStatus
import com.fuelgo.app.domain.model.PumpStatus
import com.fuelgo.app.util.startNavigation

/**
 * 1x1 circle widget: nearest pump name + open/closed status dot.
 * Renders exclusively from the cached [WidgetState] — never blocks on network.
 * Tapping launches Google Maps navigation to the nearest pump in one step.
 */
object FuelWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val state = (context.applicationContext as FuelGoApp).container.prefs.widgetState()
        provideContent { Content(state) }
    }

    @Composable
    private fun Content(state: WidgetState?) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ImageProvider(R.drawable.widget_circle))
                .clickable(actionRunCallback<NavigateAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "\u25CF", // filled circle glyph, colored by status
                    style = TextStyle(
                        color = ColorProvider(statusColor(state)),
                        fontSize = 14.sp,
                    ),
                )
                Text(
                    text = state?.name?.take(12) ?: "FuelGo",
                    style = TextStyle(
                        color = ColorProvider(Color(0xFF212121)),
                        fontSize = 9.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }

    private fun statusColor(state: WidgetState?): Color =
        when (state?.status?.let { s -> runCatching { PumpStatus.valueOf(s) }.getOrNull() }) {
            PumpStatus.OPEN -> Color(0xFF2E7D32)
            PumpStatus.CLOSED -> Color(0xFFC62828)
            else -> Color(0xFF9E9E9E)
        }
}

/** Widget tap: one intent straight into Maps navigation to the nearest pump. */
class NavigateAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val container = (context.applicationContext as FuelGoApp).container
        val state = container.prefs.widgetState()
        val openApp = {
            context.startActivity(
                Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        if (state == null) {
            openApp()
        } else {
            context.startNavigation(state.lat, state.lon, openApp)
        }
    }
}

/** Receiver declared in the manifest. */
class FuelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FuelWidget
}
