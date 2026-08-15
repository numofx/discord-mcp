package dev.saseq.schedulers;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps a "Total Members: N" display channel accurate by renaming it on a schedule.
 *
 * <p>Discord rate-limits channel renames to roughly two per ten minutes per channel, which
 * is why this polls slowly and skips the call entirely when the name has not changed,
 * rather than reacting to join and leave events. A member-count channel that updates
 * instantly is not achievable; one that updates within ten minutes is.
 *
 * <p>Disabled unless MEMBER_COUNT_CHANNEL_ID is set.
 */
@Component
public class MemberCountUpdater {

    private static final Logger log = LoggerFactory.getLogger(MemberCountUpdater.class);

    private final JDA jda;

    @Value("${MEMBER_COUNT_CHANNEL_ID:}")
    private String channelId;

    /** Must contain {count}. The rendered result becomes the channel name. */
    @Value("${MEMBER_COUNT_FORMAT:Total Members: {count}}")
    private String format;

    /**
     * Off by default so the number matches the count Discord itself shows, which is what a
     * channel labelled "Total Members" implies. Turn on to count humans only — worth pairing
     * with a label that says so.
     */
    @Value("${MEMBER_COUNT_EXCLUDE_BOTS:false}")
    private boolean excludeBots;

    public MemberCountUpdater(JDA jda) {
        this.jda = jda;
    }

    @Scheduled(initialDelayString = "${MEMBER_COUNT_INITIAL_DELAY_MS:30000}",
            fixedDelayString = "${MEMBER_COUNT_INTERVAL_MS:600000}")
    public void refresh() {
        if (channelId.isEmpty()) {
            return;
        }
        GuildChannel channel = jda.getGuildChannelById(channelId);
        if (channel == null) {
            log.warn("MEMBER_COUNT_CHANNEL_ID {} does not resolve to a channel", channelId);
            return;
        }
        String wanted = format.replace("{count}", String.valueOf(count(channel.getGuild())));
        if (wanted.equals(channel.getName())) {
            return; // Nothing changed — never spend a rename on it.
        }
        channel.getManager().setName(wanted).reason("Member count update").queue(
                ok -> log.info("Member count channel renamed to \"{}\"", wanted),
                err -> log.warn("Could not rename member count channel: {}", err.getMessage()));
    }

    private long count(Guild guild) {
        if (!excludeBots) {
            return guild.getMemberCount();
        }
        // Falls back to the total if the member cache is not populated, which is better than
        // publishing a count of zero.
        long humans = guild.getMemberCache().stream().filter(m -> !m.getUser().isBot()).count();
        return humans > 0 ? humans : guild.getMemberCount();
    }
}
