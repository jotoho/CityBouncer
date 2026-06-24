package de.jotoho.cityrp.citybouncer;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.GuildBanEvent;
import net.dv8tion.jda.api.events.guild.GuildUnbanEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class JoinAdapter extends ListenerAdapter {
    final Config config;

    @Override
    public void onGuildMemberJoin(final GuildMemberJoinEvent event) {
        if (!event.getGuild().getId().equals(config.parentServerID())) {
            final Member newbie = event.getMember();
            final User newbieUser = newbie.getUser();
            if (newbieUser.isBot())
                return;
            final Guild parentGuild = event.getJDA().getGuildById(config.parentServerID());
            if (Objects.nonNull(parentGuild)) {
                parentGuild.loadMembers().onSuccess(parentMembers -> {
                    final boolean isparentMember = parentMembers.parallelStream()
                            .map(Member::getUser)
                            .anyMatch(user -> Objects.equals(user, newbieUser));
                    if (!isparentMember) {
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
                            if (childMember.getUser().equals(leavingUser)) {
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
                        guild.unban(event.getUser()).queue();
                    });
        }
    }

    public JoinAdapter(final Config config) {
        this.config = Objects.requireNonNull(config);
    }
}
