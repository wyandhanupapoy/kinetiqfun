package org.example.kinetiqfun

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class GameAdapter(
    private val games: List<GameItem>,
    private val onClick: (GameItem, Int) -> Unit
) : RecyclerView.Adapter<GameAdapter.GameViewHolder>() {

    class GameViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtTitle: TextView = view.findViewById(R.id.txtGameTitle)
        val imgScreenshot: ImageView = view.findViewById(R.id.imgScreenshot)
        val blurOverlay: View = view.findViewById(R.id.blurOverlay)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GameViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_game_card, parent, false)
        return GameViewHolder(view)
    }

    override fun onBindViewHolder(holder: GameViewHolder, position: Int) {
        val game = games[position]
        holder.txtTitle.text = game.title
        
        val resId = game.screenshotResId
        if (resId != null) {
            holder.imgScreenshot.setImageResource(resId)
        } else {
            holder.imgScreenshot.setImageResource(android.R.color.black)
        }

        holder.itemView.setOnClickListener { onClick(game, position) }
    }

    override fun getItemCount() = games.size
}
