package dev.saseq.listeners;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Support tickets. An "Open ticket" button in the panel channel creates a private channel
 * under the tickets category, visible only to its author and the staff role, with
 * claim/close/reopen/delete controls.
 *
 * <p>Disabled unless TICKETS_CATEGORY_ID and TICKET_STAFF_ROLE_ID are set.
 */
@Component
public class TicketListener extends ListenerAdapter {

    private static final Logger log = LoggerFactory.getLogger(TicketListener.class);

    public static final String OPEN = "ticket:open";
    private static final String CLAIM = "ticket:claim";
    private static final String CLOSE = "ticket:close";
    private static final String REOPEN = "ticket:reopen";
    private static final String DELETE = "ticket:delete";

    /** The number in a ticket channel name, e.g. "ticket-12". */
    private static final Pattern TICKET_NUMBER = Pattern.compile("^ticket-(\\d+)$");

    @Value("${TICKETS_CATEGORY_ID:}")
    private String ticketsCategoryId;

    @Value("${TICKET_STAFF_ROLE_ID:}")
    private String staffRoleId;

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith("ticket:") || !event.isFromGuild() || !isConfigured()) {
            return;
        }
        switch (id) {
            case OPEN -> openTicket(event);
            case CLAIM -> claimTicket(event);
            case CLOSE -> setOpen(event, false);
            case REOPEN -> setOpen(event, true);
            case DELETE -> deleteTicket(event);
            default -> { }
        }
    }

    private void openTicket(ButtonInteractionEvent event) {
        Guild guild = event.getGuild();
        Category category = guild.getCategoryById(ticketsCategoryId);
        Role staff = guild.getRoleById(staffRoleId);
        if (category == null || staff == null) {
            event.reply("Ticketing is misconfigured — ask an admin to check TICKETS_CATEGORY_ID and TICKET_STAFF_ROLE_ID.")
                    .setEphemeral(true).queue();
            return;
        }
        Member author = event.getMember();
        if (hasOpenTicket(category, author)) {
            event.reply("You already have an open ticket. Please use it, or close it before opening another.")
                    .setEphemeral(true).queue();
            return;
        }

        int number = nextTicketNumber(category);
        EnumSet<Permission> access = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND,
                Permission.MESSAGE_HISTORY, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_ADD_REACTION);

        category.createTextChannel("ticket-" + number)
                .setTopic("Ticket #" + number + " — created by " + author.getUser().getName()
                        + " (" + author.getId() + ")")
                .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(author, access, null)
                .addPermissionOverride(staff, access, null)
                .reason("Support ticket opened by " + author.getUser().getName())
                .queue(channel -> {
                    channel.sendMessage("Your help request has been created.\nHow can we help you? Please describe your issue below and a member of the Numo team will assist you shortly.")
                            .addComponents(ActionRow.of(
                                    Button.primary(CLAIM, "Claim"),
                                    Button.secondary(CLOSE, "Close"),
                                    Button.success(REOPEN, "Reopen"),
                                    Button.danger(DELETE, "Delete")))
                            // Pinning needs PIN_MESSAGES, which the bot may not have. A ticket
                            // that works but is unpinned is fine; a failed pin must not surface
                            // as an error on an otherwise successful open.
                            .queue(m -> m.pin().queue(ok -> { },
                                    err -> log.info("Ticket panel not pinned ({}). Grant the bot Pin Messages to enable it.",
                                            err.getMessage())));
                    event.reply("Ticket #" + number + " created: " + channel.getAsMention())
                            .setEphemeral(true).queue();
                    log.info("Opened ticket #{} for {}", number, author.getId());
                }, err -> {
                    log.warn("Failed to open ticket for {}: {}", author.getId(), err.getMessage());
                    event.reply("Could not create your ticket. Please tell a team member.")
                            .setEphemeral(true).queue();
                });
    }

    private void claimTicket(ButtonInteractionEvent event) {
        if (staffOnly(event)) {
            return;
        }
        event.reply("Claimed by " + event.getMember().getAsMention() + ".").queue();
    }

    /** Close hides the ticket from its author but keeps the transcript for staff. */
    private void setOpen(ButtonInteractionEvent event, boolean open) {
        if (staffOnly(event)) {
            return;
        }
        TextChannel channel = event.getChannel().asTextChannel();
        String authorId = authorIdFromTopic(channel.getTopic());
        if (authorId == null) {
            event.reply("Cannot find who opened this ticket — its topic was changed.").setEphemeral(true).queue();
            return;
        }
        event.getGuild().retrieveMemberById(authorId).queue(author -> {
            var perms = EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY);
            var update = open
                    ? channel.upsertPermissionOverride(author).grant(perms)
                    : channel.upsertPermissionOverride(author).deny(perms);
            update.reason(open ? "Ticket reopened" : "Ticket closed").queue(
                    ok -> event.reply(open ? "Ticket reopened." : "Ticket closed. Staff can still read it.").queue(),
                    err -> event.reply("Failed: " + err.getMessage()).setEphemeral(true).queue());
        }, err -> event.reply("The member who opened this ticket has left the server.").queue());
    }

    private void deleteTicket(ButtonInteractionEvent event) {
        if (staffOnly(event)) {
            return;
        }
        event.reply("Deleting this ticket…").queue(hook ->
                event.getChannel().asTextChannel().delete()
                        .reason("Ticket deleted by " + event.getUser().getName()).queue());
    }

    /** Replies and returns true when the clicker is not staff. */
    private boolean staffOnly(ButtonInteractionEvent event) {
        Member member = event.getMember();
        boolean staff = member != null && member.getRoles().stream().anyMatch(r -> r.getId().equals(staffRoleId));
        if (!staff) {
            event.reply("Only the Numo team can use this.").setEphemeral(true).queue();
        }
        return !staff;
    }

    private boolean hasOpenTicket(Category category, Member author) {
        return category.getTextChannels().stream()
                .anyMatch(c -> author.getId().equals(authorIdFromTopic(c.getTopic()))
                        && c.getPermissionOverride(author) != null
                        && !c.getPermissionOverride(author).getDenied().contains(Permission.VIEW_CHANNEL));
    }

    /**
     * Ticket numbers come from the existing channel names rather than stored state, so a
     * restart cannot reset the counter or reuse a number.
     */
    private int nextTicketNumber(Category category) {
        return category.getTextChannels().stream()
                .map(c -> TICKET_NUMBER.matcher(c.getName()))
                .filter(Matcher::find)
                .mapToInt(m -> Integer.parseInt(m.group(1)))
                .max().orElse(0) + 1;
    }

    private String authorIdFromTopic(String topic) {
        if (topic == null) {
            return null;
        }
        int open = topic.lastIndexOf('('), close = topic.lastIndexOf(')');
        return (open >= 0 && close > open) ? topic.substring(open + 1, close) : null;
    }

    private boolean isConfigured() {
        return !ticketsCategoryId.isEmpty() && !staffRoleId.isEmpty();
    }
}
