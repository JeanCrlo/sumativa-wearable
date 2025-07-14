package com.example.dhlwear.mobile.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.example.dhlwear.mobile.R
import com.example.dhlwear.mobile.model.Message

/**
 * Adaptador personalizado para mostrar mensajes en una lista
 * con diferentes estilos según el origen (reloj o teléfono)
 */
class MessageAdapter(
    context: Context,
    private val messages: List<Message>
) : ArrayAdapter<Message>(context, 0, messages) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var itemView = convertView
        
        if (itemView == null) {
            itemView = LayoutInflater.from(context)
                .inflate(R.layout.message_item, parent, false)
        }
        
        val message = messages[position]
        
        // Referencias a las vistas
        val messageCard = itemView!!.findViewById<CardView>(R.id.messageCard)
        val messageText = itemView.findViewById<TextView>(R.id.messageText)
        val messageTime = itemView.findViewById<TextView>(R.id.messageTime)
        val senderName = itemView.findViewById<TextView>(R.id.senderName)
        
        // Configurar contenido
        messageText.text = message.text
        messageTime.text = message.getFormattedTime()
        
        // Configurar estilo según el origen
        if (message.isFromWatch) {
            messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.message_received))
            senderName.text = "Reloj"
            senderName.visibility = View.VISIBLE
            // Alinear a la izquierda
            (messageCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                marginStart = 10
                marginEnd = 80
            }
        } else {
            messageCard.setCardBackgroundColor(ContextCompat.getColor(context, R.color.message_sent))
            senderName.visibility = View.GONE
            // Alinear a la derecha
            (messageCard.layoutParams as ViewGroup.MarginLayoutParams).apply {
                marginStart = 80
                marginEnd = 10
            }
        }
        
        return itemView
    }
}
