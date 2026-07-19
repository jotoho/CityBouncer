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
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class JoinAdapter extends ListenerAdapter {
    private final System.Logger logger = System.getLogger(JoinAdapter.class.getCanonicalName());
    private final Config config;
    private boolean isParentMembersLoaded = false;

    @Override
    public void onGuildMemberJoin(final @NotNull GuildMemberJoinEvent event) {
        if (!this.isParentMembersLoaded)
            // can't process it rn
            return;

        if (!event.getGuild().getId().equals(config.parentServerID())) {
            final Member newbie = event.getMember();
            final User newbieUser = newbie.getUser();
            if (newbieUser.isBot() || newbieUser.isSystem() || event.getJDA().getSelfUser().equals(newbieUser)) {
                return;
            }
            if (config.servers().getOrDefault(event.getGuild().getId(), Config.DEFAULT).disableKickOnJoin()) {
                return;
            }
            final Guild parentGuild = event.getJDA().getGuildById(config.parentServerID());
            if (Objects.nonNull(parentGuild)) {
                if (!parentGuild.isMember(newbieUser)) {
                    logger.log(System.Logger.Level.INFO, "Kicking stranger newbie " + newbieUser.getAsTag() + " from Guild " + event.getGuild().getName());
                    newbieUser.openPrivateChannel()
                            .onSuccess(dmChannel -> {
                                dmChannel.sendMessage("You've been automatically kicked from " + event.getGuild().getName() + " because all members also need to be members of the main CityRP server.")
                                        .queue((_) -> newbie.kick().reason("user not in main CityRP server").queue(),
                                                (_) -> newbie.kick().reason("user not in main CityRP server").queue());
                            }).queue();
                    newbie.kick().queue();
                }
            }
            else {
                System.err.println("Join Event: Cannot find parent Guild");
            }
        }
    }

    @Override
    public void onGuildMemberRemove(final @NotNull GuildMemberRemoveEvent event) {
        if (!this.isParentMembersLoaded)
            // can't process it rn
            return;

        if (event.getGuild().getId().equals(config.parentServerID())) {
            if (event.getUser().isBot()) {
                return;
            }

            final User leavingUser = event.getUser();
            event.getJDA().getGuilds()
                    .parallelStream()
                    .filter(guild -> !guild.getId().equals(config.parentServerID()))
                    .forEach(guild -> {
                        if (config.servers().getOrDefault(guild.getId(), Config.DEFAULT).disableKickOnLeave()) {
                            return;
                        }

                        guild.getMembers().forEach(childMember -> {
                            final var childUser = childMember.getUser();
                            if (childUser.equals(leavingUser)) {
                                if (childUser.isBot() || childUser.isSystem() || event.getJDA().getSelfUser().equals(childUser))
                                    return;
                                logger.log(System.Logger.Level.INFO, "Kicking stranger newbie " + childUser.getAsTag() + " from Guild " + guild.getName());
                                childUser.openPrivateChannel()
                                        .onSuccess(dmChannel -> {
                                            dmChannel.sendMessage("You've been automatically kicked from " + guild.getName() + " because all members also need to be members of the main CityRP server.")
                                                    .queue((_) -> childMember.kick().reason("user not in main CityRP server").queue(),
                                                            (_) -> childMember.kick().reason("user not in main CityRP server").queue());
                                        }).queue();

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
                        if (config.getServerConfig(guild.getId()).disableBanForwarding()) {
                            return;
                        }
                        logger.log(System.Logger.Level.INFO, "Banning " + event.getUser().getAsTag() + " from guild " + guild.getName());
                        guild.ban(event.getUser(), 0, TimeUnit.SECONDS).reason("Forwarded from main CityRP server").queue();
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
                        if (config.getServerConfig(guild.getId()).disableBanForwarding()) {
                            return;
                        }
                        logger.log(System.Logger.Level.INFO, "Unbanning " + event.getUser().getAsTag() + " from guild " + guild.getName());
                        guild.unban(event.getUser()).reason("Forwarded from main CityRP server").queue();
                    });
        }
    }

    private void processGuild(final JDA api, final Guild childGuild) {
        logger.log(System.Logger.Level.INFO, "Processing ready guild " + childGuild.getName());
        final Guild parentGuild = api.getGuildById(config.parentServerID());
        final var childGuildMembers = childGuild.loadMembers().setTimeout(Duration.ofMinutes(5));
        if (parentGuild == null) {
            logger.log(System.Logger.Level.WARNING, "parentGuild not found during ready event processing");
            return;
        }
        else if (Objects.equals(childGuild, parentGuild)) {
            childGuildMembers.onSuccess(_ -> isParentMembersLoaded = true);
            return;
        }
        else if (config.getServerConfig(childGuild.getId()).disableKickExisting()) {
            return;
        }

        final List<User> childGuildUsers = childGuildMembers.get()
                .stream()
                .map(Member::getUser)
                .toList();
        if (!this.isParentMembersLoaded) {
            try {
                Thread.sleep(Duration.ofMinutes(1));
            } catch (final InterruptedException _) {

            }

            if (!this.isParentMembersLoaded) {
                logger.log(System.Logger.Level.WARNING, "waiting for parent guild member list to be ready took too long");
                return;
            }
        }
        final List<User> trespassers = parentGuild.getMembers()
                .stream()
                .map(Member::getUser)
                .filter(Predicate.not(childGuildUsers::contains))
                .toList();
        for (final var trespasser : trespassers) {
            if (trespasser.isBot() || trespasser.isSystem() || api.getSelfUser().equals(trespasser))
                continue;
            logger.log(System.Logger.Level.INFO, "Kicking user " + trespasser.getGlobalName() + " from guild " + childGuild.getName());

            childGuild.kick(trespasser).queue();
            trespasser.openPrivateChannel()
                    .onSuccess(dmChannel -> {
                        dmChannel.sendMessage("You've been automatically kicked from " + childGuild.getName() + " because all members also need to be members of the main CityRP server.")
                                .queue((_) -> childGuild.kick(trespasser).reason("user not in main CityRP server").queue(),
                                        (_) -> childGuild.kick(trespasser).reason("user not in main CityRP server").queue());
                    }).queue();
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

    @Override
    public void onSessionRecreate(@NotNull SessionRecreateEvent event) {
        super.onSessionRecreate(event);
        this.isParentMembersLoaded = false;
    }

    public JoinAdapter(final Config config) {
        this.config = Objects.requireNonNull(config);
    }
}
