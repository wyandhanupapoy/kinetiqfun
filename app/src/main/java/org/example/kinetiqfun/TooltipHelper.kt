package org.example.kinetiqfun

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Helper class for showing tooltips
 */
object TooltipHelper {

    /**
     * Shows a tooltip relative to the anchor view
     * @param context The context
     * @param anchorView The view to anchor the tooltip to
     * @param message The message to display
     * @param gravity Where to position the tooltip relative to the anchor (Gravity.BOTTOM or Gravity.TOP)
     */
    fun show(context: Context, anchorView: View, message: String, gravity: Int = Gravity.BOTTOM) {
        val tooltipView = LayoutInflater.from(context).inflate(R.layout.layout_tooltip, null).apply {
            findViewById<TextView>(R.id.tooltipMessage).text = message
        }

        val popupWindow = PopupWindow(
            tooltipView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        tooltipView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val tooltipWidth = tooltipView.measuredWidth
        val tooltipHeight = tooltipView.measuredHeight

        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)

        val x = location[0] + anchorView.width / 2 - tooltipWidth / 2
        val y = if (gravity == Gravity.BOTTOM) {
            location[1] + anchorView.height + 20
        } else {
            location[1] - tooltipHeight - 20
        }

        // Ensure it stays on screen
        val finalX = maxOf(0, x)

        popupWindow.showAtLocation(anchorView.rootView, Gravity.NO_GRAVITY, finalX, y)

        popupWindow.contentView.postDelayed({
            if (popupWindow.isShowing) {
                try {
                    popupWindow.dismiss()
                } catch (e: Exception) {
                    // Ignore if activity is already destroyed
                }
            }
        }, 5000)
    }

    /**
     * Shows a tooltip above a specific point in the anchor view
     * @param context The context
     * @param anchorView The view to anchor the tooltip to (used to get screen position)
     * @param message The message to display in the tooltip
     * @param pointX The x-coordinate in the anchor view where the tooltip should point to
     * @param pointY The y-coordinate in the anchor view where the tooltip should point to
     */
    fun showAtPoint(context: Context, anchorView: View, message: String, pointX: Int, pointY: Int) {
        // Inflate the tooltip layout
        val tooltipView = LayoutInflater.from(context).inflate(R.layout.layout_tooltip, null).apply {
            findViewById<TextView>(R.id.tooltipMessage).text = message
        }

        // Create the popup window
        val popupWindow = PopupWindow(
                tooltipView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true // focusable
        ).apply {
            // Set background to make it work properly
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        // Get the anchor view's location on screen
        val location = IntArray(2)
        anchorView.getLocationOnScreen(location)
        val anchorX = location[0]
        val anchorY = location[1]

        // Calculate the point on screen
        val screenX = anchorX + pointX
        val screenY = anchorY + pointY

        // Measure the tooltip view to get its dimensions
        tooltipView.measure(
                View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED
        )
        val tooltipWidth = tooltipView.measuredWidth
        val tooltipHeight = tooltipView.measuredHeight

        // Position the tooltip above the point
        var x = screenX - tooltipWidth / 2
        var y = screenY - tooltipHeight - 20   // 20px gap between point and tooltip

        // Show the popup window
        popupWindow.showAtLocation(anchorView.rootView, Gravity.NO_GRAVITY, x, y)

        // Dismiss after 5 seconds
        popupWindow.contentView.postDelayed({
            popupWindow.dismiss()
        }, 5000)
    }
}