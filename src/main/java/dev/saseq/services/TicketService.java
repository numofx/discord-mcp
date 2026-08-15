package dev.saseq.services;

import dev.saseq.listeners.TicketListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

@Service
public class TicketService {

    private final JDA jda;

    public TicketService(JDA jda) {
        this.jda = jda;
    }

    /**
     * Posts the ticket panel — the message whose button opens a support ticket. Buttons are
     * message components, so they cannot be sent through the plain send_message tool.
     *
     * @param channelId ID of the channel to post the panel in.
     * @param message   Optional panel text. A default is used when omitted.
     * @return A confirmation containing the message link.
     */
    @Tool(name = "post_ticket_panel", description = "Post the support ticket panel with an 'Open ticket' button to a channel")
    public String postTicketPanel(@ToolParam(description = "Discord channel ID") String channelId,
                                  @ToolParam(description = "Panel message text", required = false) String message) {
        if (channelId == null || channelId.isEmpty()) {
            throw new IllegalArgumentException("channelId cannot be empty");
        }
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("No text channel found with id " + channelId);
        }
        String body = (message == null || message.isEmpty())
                ? "**Need some help?**\nClick the 'Open ticket' button below to get in contact with the Numo team."
                : message;

        var sent = channel.sendMessage(body)
                .addComponents(ActionRow.of(
                        Button.danger(TicketListener.OPEN, "Open ticket").withEmoji(Emoji.fromUnicode("📩"))))
                .complete();
        return "Ticket panel posted. Message link: " + sent.getJumpUrl();
    }
}
