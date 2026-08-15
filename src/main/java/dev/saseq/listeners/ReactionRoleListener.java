package dev.saseq.listeners;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.entities.emoji.EmojiUnion;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reaction-role gate: grants a role to anyone who reacts to a specific message with a
 * specific emoji. Used to build a "verify-here" channel where reacting to the rules post
 * reveals the rest of the server.
 *
 * <p>Disabled unless VERIFY_MESSAGE_ID, VERIFY_EMOJI and VERIFY_ROLE_ID are all set.
 */
@Component
public class ReactionRoleListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ReactionRoleListener.class);

    @Value("${VERIFY_MESSAGE_ID:}")
    private String verifyMessageId;

    @Value("${VERIFY_EMOJI:}")
    private String verifyEmoji;

    @Value("${VERIFY_ROLE_ID:}")
    private String verifyRoleId;

    /**
     * Whether removing the reaction should also remove the role. Off by default so that a
     * stray un-react cannot silently lock a member out of the server.
     */
    @Value("${VERIFY_REMOVE_ON_UNREACT:false}")
    private boolean removeOnUnreact;

    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (!isVerifyReaction(event)) {
            return;
        }
        Guild guild = event.getGuild();
        Role role = guild.getRoleById(verifyRoleId);
        if (role == null) {
            log.warn("VERIFY_ROLE_ID {} does not resolve to a role in guild {}", verifyRoleId, guild.getId());
            return;
        }
        guild.addRoleToMember(UserSnowflake.fromId(event.getUserId()), role)
                .reason("Verified via reaction gate")
                .queue(
                        ok -> log.info("Granted {} to user {}", role.getName(), event.getUserId()),
                        err -> log.warn("Failed to grant {} to user {}: {}", role.getName(), event.getUserId(), err.getMessage()));
    }

    @Override
    public void onMessageReactionRemove(@NotNull MessageReactionRemoveEvent event) {
        if (!removeOnUnreact || !isVerifyReaction(event)) {
            return;
        }
        Guild guild = event.getGuild();
        Role role = guild.getRoleById(verifyRoleId);
        if (role == null) {
            return;
        }
        guild.removeRoleFromMember(UserSnowflake.fromId(event.getUserId()), role)
                .reason("Un-reacted from the verification gate")
                .queue(
                        ok -> log.info("Removed {} from user {}", role.getName(), event.getUserId()),
                        err -> log.warn("Failed to remove {} from user {}: {}", role.getName(), event.getUserId(), err.getMessage()));
    }

    private boolean isVerifyReaction(GenericMessageReactionEvent event) {
        if (!isConfigured() || !event.isFromGuild()) {
            return false;
        }
        // The bot seeds the reaction on the gate post; that must not grant it the role.
        if (event.getUserId().equals(event.getJDA().getSelfUser().getId())) {
            return false;
        }
        return event.getMessageId().equals(verifyMessageId) && matchesEmoji(event.getEmoji());
    }

    private boolean matchesEmoji(EmojiUnion emoji) {
        // Unicode emoji arrive as the literal character, custom ones as <:name:id>.
        return emoji.getFormatted().equals(verifyEmoji) || emoji.getName().equals(verifyEmoji);
    }

    private boolean isConfigured() {
        return !verifyMessageId.isEmpty() && !verifyEmoji.isEmpty() && !verifyRoleId.isEmpty();
    }
}
