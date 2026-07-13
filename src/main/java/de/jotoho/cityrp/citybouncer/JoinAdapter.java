package de.jotoho.cityrp.citybouncer;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class JoinAdapter extends ListenerAdapter {
    final System.Logger logger = System.getLogger(JoinAdapter.class.getCanonicalName());
    final Config config;

    @Override
    public void onGuildMemberJoin(final GuildMemberJoinEvent event) {
        if (!event.getGuild().getId().equals(config.parentServerID())) {
            final Member newbie = event.getMember();
            final User newbieUser = newbie.getUser();
            if (newbieUser.isBot() || newbieUser.isSystem() || event.getJDA().getSelfUser().equals(newbieUser))
                return;
            final Guild parentGuild = event.getJDA().getGuildById(config.parentServerID());
            if (Objects.nonNull(parentGuild)) {
                parentGuild.loadMembers().onSuccess(parentMembers -> {
                    final boolean isParentMember = parentMembers.parallelStream()
                            .map(Member::getUser)
                            .anyMatch(user -> Objects.equals(user, newbieUser));
                    if (!isParentMember) {
                        logger.log(System.Logger.Level.INFO, "Kicking stranger newbie " + newbieUser.getAsTag() + " from Guild " + event.getGuild().getName());
                        newbie.kick().queue();
                    }
                });
            }
            else {
                System.err.println("Join Event: Cannot find parent Guild");
            }
        }
    }

    @Override
    public void onGuildMemberRemove(final GuildMemberRemoveEvent event) {
        if (event.getGuild().getId().equals(config.parentServerID())) {
            if (event.getUser().isBot()) {
                return;
            }

            final User leavingUser = event.getUser();
            event.getJDA().getGuilds()
                    .parallelStream()
                    .filter(guild -> !guild.getId().equals(config.parentServerID()))
                    .forEach(guild -> {
                        guild.loadMembers(childMember -> {
                            final var childUser = childMember.getUser();
                            if (childUser.equals(leavingUser)) {
                                if (childUser.isBot() || childUser.isSystem() || event.getJDA().getSelfUser().equals(childUser))
                                    return;
                                logger.log(System.Logger.Level.INFO, "Kicking stranger newbie " + childUser.getAsTag() + " from Guild " + guild.getName());
                                childMember.kick().queue();
                            }
                        });
                    });
        }
    }

    @Override
    public void onGuildBan(final GuildBanEvent event) {
        if (event.getGuild().getId().equals(config.parentServerID())) {
            event.getJDA().getGuilds()
                    .parallelStream()
                    .filter(guild -> !guild.getId().equals(config.parentServerID()))
                    .forEach(guild -> {
                        logger.log(System.Logger.Level.INFO, "Banning " + event.getUser().getAsTag() + " from guild " + guild.getName());
                        guild.ban(event.getUser(), 0, TimeUnit.SECONDS).queue();
                    });
        }
    }

    @Override
    public void onGuildUnban(final GuildUnbanEvent event) {
        if (event.getGuild().getId().equals(config.parentServerID())) {
            event.getJDA().getGuilds()
                    .parallelStream()
                    .filter(guild -> !guild.getId().equals(config.parentServerID()))
                    .forEach(guild -> {
                        logger.log(System.Logger.Level.INFO, "Unbanning " + event.getUser().getAsTag() + " from guild " + guild.getName());
                        guild.unban(event.getUser()).queue();
                    });
        }
    }

    private void processGuild(final JDA api, final Guild childGuild) {
        logger.log(System.Logger.Level.INFO, "Processing ready guild " + childGuild.getName());
        final Guild parentGuild = api.getGuildById(config.parentServerID());
        if (parentGuild == null) {
            logger.log(System.Logger.Level.WARNING, "parentGuild not found during ready event processing");
            return;
        }
        else if (Objects.equals(childGuild, parentGuild)) {
            return;
        }

        final List<User> childGuildMembers = childGuild.loadMembers().setTimeout(Duration.ofMinutes(10)).get().stream().map(Member::getUser).toList();
        final List<User> matchingInParent = parentGuild.findMembers(member -> childGuildMembers.contains(member.getUser()))
                .setTimeout(Duration.ofMinutes(10))
                .get()
                .stream()
                .map(Member::getUser)
                .toList();

        final List<User> trespassers = new LinkedList<>(childGuildMembers);
        trespassers.removeAll(matchingInParent);
        for (final var trespasser : trespassers) {
            if (trespasser.isBot() || trespasser.isSystem() || api.getSelfUser().equals(trespasser))
                continue;
            logger.log(System.Logger.Level.INFO, "Kicking user " + trespasser.getGlobalName() + " from guild " + childGuild.getName());
            childGuild.kick(trespasser).queue();
        }
    }

    @Override
    public void onGuildReady(@NotNull GuildReadyEvent event) {
        super.onGuildReady(event);
        this.processGuild(event.getJDA(), event.getGuild());
    }

    @Override
    public void onGuildJoin(@NotNull GuildJoinEvent event) {
        super.onGuildJoin(event);
        this.processGuild(event.getJDA(), event.getGuild());
    }

    public JoinAdapter(final Config config) {
        this.config = Objects.requireNonNull(config);
    }
}
