package de.jotoho.cityrp.citybouncer;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.SelfMember;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.concurrent.Task;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import static java.lang.System.Logger.Level.*;

import java.lang.ref.WeakReference;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class JoinAdapter extends ListenerAdapter {
    private final System.Logger logger = System.getLogger(JoinAdapter.class.getCanonicalName());
    private final Config config;
    private WeakReference<Task<?>> parentGuildLoadTask = new WeakReference<>(null);
    private JDA api = null;
    private final List<Predicate<User>> excludedUserTypes = List.of(
            User::isBot,
            User::isSystem,
            user -> Objects.isNull(api) || api.getSelfUser().equals(user)
    );

    private void kickUser(final @NotNull Member member,
                          final @Nullable String reason,
                          final @Nullable String dmReason) {
        Objects.requireNonNull(member);
        for (final var disqualificationPredicate : excludedUserTypes) {
            if (disqualificationPredicate.test(member.getUser())) {
                return;
            }
        }
        final SelfMember selfMember = member.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.KICK_MEMBERS) || !selfMember.canInteract(member)) {
            logger.log(INFO, "Aborting kick attempt against " + member.getUser().getName()
                    + " in guild " + member.getGuild().getName() + ". Lacking permissions.");
            return;
        }
        if (config.isArmed()) {
            logger.log(INFO, "Kicking " + member.getUser().getName() + " from Guild " + member.getGuild().getName());
            if (Objects.isNull(dmReason)) {
                member.kick().reason(reason).queue();
            }
            else {
                member.getUser().openPrivateChannel()
                        .onSuccess(dmChannel -> {
                            final Consumer<Object> postMessageAction = _ -> {
                                member.kick().reason(reason).queue();
                                dmChannel.delete().queue();
                            };
                            dmChannel.sendMessage(dmReason)
                                    .queue(postMessageAction, postMessageAction);
                        }).queue();
            }
        }
        else {
            logger.log(INFO, "Would have kicked user " + member.getUser().getName() + " from Guild " + member.getGuild().getName());
        }
    }

    private void banUser(final @NotNull Member member,
                         final @Nullable String reason,
                         final @Nullable String dmReason) {
        Objects.requireNonNull(member);
        for (final var disqualificationPredicate : excludedUserTypes) {
            if (disqualificationPredicate.test(member.getUser())) {
                return;
            }
        }
        final SelfMember selfMember = member.getGuild().getSelfMember();
        if (!selfMember.hasPermission(Permission.BAN_MEMBERS) || !selfMember.canInteract(member)) {
            logger.log(INFO, "Aborting ban attempt against " + member.getUser().getName()
                    + " in guild " + member.getGuild().getName() + ". Lacking permissions.");
            return;
        }
        if (config.isArmed()) {
            logger.log(INFO, "Banning " + member.getUser().getName() + " from guild " + member.getGuild().getName());
            member.ban(0, TimeUnit.SECONDS).reason(reason).queue();
        }
        else {
            logger.log(INFO, "Would have banned user " + member.getUser().getName() + " from Guild " + member.getGuild().getName());
        }
    }

    private void unbanUser(final @NotNull Guild guild,
                           final @NotNull User user,
                           final @Nullable String reason,
                           final @Nullable String dmReason) {
        Objects.requireNonNull(guild);
        Objects.requireNonNull(user);
        for (final var disqualificationPredicate : excludedUserTypes) {
            if (disqualificationPredicate.test(user)) {
                return;
            }
        }
        final SelfMember selfMember = guild.getSelfMember();
        if (!selfMember.hasPermission(Permission.BAN_MEMBERS)) {
            logger.log(INFO, "Aborting unban attempt against " + user.getName()
                    + " in guild " + guild.getName() + ". Lacking permissions.");
            return;
        }
        if (config.isArmed()) {
            logger.log(INFO, "Unbanning " + user.getName() + " from guild " + guild.getName());
            guild.unban(user).reason(reason).queue();
        }
        else {
            logger.log(INFO, "Would have unbanned user " + user.getName() + " from Guild " + guild.getName());
        }
    }

    @Override
    public void onGuildMemberJoin(final @NotNull GuildMemberJoinEvent event) {
        if (!event.getGuild().getId().equals(config.parentServerID())) {
            if (!event.getGuild().isLoaded()) {
                logger.log(DEBUG, "Ignoring join event due to member list not being ready");
                return;
            }

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
                if (!parentGuild.isLoaded()) {
                    logger.log(DEBUG, "Ignoring join event due to member list not being ready");
                    return;
                }
                if (!parentGuild.isMember(newbieUser)) {
                    kickUser(
                        newbie,
                        "user not in parent guild",
                        "You've been automatically kicked from " + event.getGuild().getName() + " because all members also need to be members of the main CityRP server."
                    );
                }
            }
            else {
                System.err.println("Join Event: Cannot find parent Guild");
            }
        }
    }

    @Override
    public void onGuildMemberRemove(final @NotNull GuildMemberRemoveEvent event) {
        if (event.getGuild().getId().equals(config.parentServerID())) {
            if (event.getUser().isBot() || !event.getGuild().isLoaded()) {
                logger.log(DEBUG, "Ignoring leave event due to member list not being ready");
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
                        if (!guild.isLoaded()) {
                            logger.log(DEBUG, "Ignoring leave event due to member list not being ready");
                            return;
                        }

                        guild.getMembers().forEach(childMember -> {
                            final var childUser = childMember.getUser();
                            if (childUser.equals(leavingUser)) {
                                if (childUser.isBot() || childUser.isSystem() || event.getJDA().getSelfUser().equals(childUser))
                                    return;
                                kickUser(
                                    childMember,
                                    "user left the parent guild",
                                    "You've been automatically kicked from " + guild.getName() + " because all members also need to be members of the main CityRP server."
                                );
                            }
                        });
                    });
        }
    }

    @Override
    public void onGuildBan(final GuildBanEvent event) {
        if (event.getGuild().getId().equals(config.parentServerID())) {
            final User user = event.getUser();
            event.getJDA().getGuilds()
                    .parallelStream()
                    .filter(guild -> !guild.getId().equals(config.parentServerID()))
                    .forEach(guild -> {
                        if (config.getServerConfig(guild.getId()).disableBanForwarding()) {
                            return;
                        }
                        final var childGuildMember = guild.getMember(user);
                        if (Objects.isNull(childGuildMember)) {
                            return;
                        }
                        banUser(childGuildMember, "forwarded from parent guild", null);
                    });
        }
    }

    @Override
    public void onGuildUnban(final GuildUnbanEvent event) {
        if (event.getGuild().getId().equals(config.parentServerID())) {
            final User user = event.getUser();
            event.getJDA().getGuilds()
                    .parallelStream()
                    .filter(guild -> !guild.getId().equals(config.parentServerID()))
                    .forEach(guild -> {
                        if (config.getServerConfig(guild.getId()).disableBanForwarding()) {
                            return;
                        }
                        unbanUser(guild, user, "forwarded from parent guild", null);
                    });
        }
    }

    private void processGuild(final JDA api, final Guild childGuild) {
        logger.log(System.Logger.Level.INFO, "Processing ready guild " + childGuild.getName());
        final Guild parentGuild = api.getGuildById(config.parentServerID());
        final var childGuildMembersTask = childGuild.loadMembers().setTimeout(Duration.ofMinutes(1));
        if (parentGuild == null) {
            logger.log(WARNING, "parentGuild not found during ready event processing");
            this.parentGuildLoadTask = new WeakReference<>(childGuildMembersTask);
            return;
        }
        else if (Objects.equals(childGuild, parentGuild)) {
            return;
        }
        else if (config.getServerConfig(childGuild.getId()).disableKickExisting()) {
            logger.log(INFO, "Cleanup disabled on guild: " + childGuild.getName());
            return;
        }

        final List<User> childGuildUsers = childGuildMembersTask.get()
                .stream()
                .map(Member::getUser)
                .toList();
        if (!parentGuild.isLoaded()) {
            try {
                Thread.sleep(Duration.ofMinutes(1));
            } catch (final InterruptedException _) {

            }

            if (!parentGuild.isLoaded()) {
                logger.log(System.Logger.Level.WARNING, "waiting for parent guild member list to be ready took too long");
                return;
            }
        }

        if (!parentGuild.isLoaded() || !childGuild.isLoaded()) {
            logger.log(WARNING, "member lists not ready during cleanup, skipping guild.");
            return;
        }

        {
            // waiting for parent guild to finish loading
            final var parentGuildLoading = this.parentGuildLoadTask.get();
            if (Objects.nonNull(parentGuildLoading)) {
                parentGuildLoading.get();
            }
        }

        final List<User> trespassers = childGuildUsers.stream()
                .filter(Predicate.not(parentGuild::isMember))
                .toList();
        for (final var trespasser : trespassers) {
            if (trespasser.isBot() || trespasser.isSystem() || api.getSelfUser().equals(trespasser))
                continue;

            final Member memberToKick = childGuild.getMember(trespasser);
            if (Objects.isNull(memberToKick)) {
                continue;
            }
            kickUser(memberToKick,
                    "cleanup: user not found in parent server",
                    "You've been automatically kicked from " + childGuild.getName() + " because all members also need to be members of the main CityRP server.");
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
    public void onReady(@NotNull ReadyEvent event) {
        super.onReady(event);
        this.api = event.getJDA();
        this.api.getPrivateChannels()
                .stream()
                .peek(dmChannel -> logger.log(INFO, "Closing orphaned DM channel: " + dmChannel.getName()))
                .map(PrivateChannel::delete)
                .forEach(RestAction::queue);
    }

    public JoinAdapter(final Config config) {
        this.config = Objects.requireNonNull(config);
    }
}
