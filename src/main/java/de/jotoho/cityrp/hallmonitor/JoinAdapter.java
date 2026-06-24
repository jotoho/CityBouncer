package de.jotoho.cityrp.hallmonitor;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Objects;

public class JoinAdapter extends ListenerAdapter {
    final Config config;

    @Override
    public void onGuildMemberJoin(final GuildMemberJoinEvent event) {
        if (!event.getGuild().getId().equals(config.masterServerID())) {
            final Member newbie = event.getMember();
            final User newbieUser = newbie.getUser();
            if (newbieUser.isBot())
                return;
            final Guild masterGuild = event.getJDA().getGuildById(config.masterServerID());
            if (Objects.nonNull(masterGuild)) {
                masterGuild.loadMembers().onSuccess(masterMembers -> {
                    final boolean isMasterMember = masterMembers.parallelStream()
                            .map(Member::getUser)
                            .anyMatch(user -> Objects.equals(user, newbieUser));
                    if (!isMasterMember) {
                        newbie.kick().queue();
                    }
                });
            }
            else {
                System.err.println("Join Event: Cannot find Master Guild");
            }
        }
    }

    @Override
    public void onGuildMemberRemove(final GuildMemberRemoveEvent event) {
        if (event.getGuild().getId().equals(config.masterServerID())) {
            if (event.getUser().isBot()) {
                return;
            }

            final User leavingUser = event.getUser();
            event.getJDA().getGuilds()
                    .parallelStream()
                    .filter(guild -> !guild.getId().equals(config.masterServerID()))
                    .forEach(guild -> {
                        guild.loadMembers(slaveMember -> {
                            if (slaveMember.getUser().equals(leavingUser)) {
                                slaveMember.kick().queue();
                            }
                        });
                    });
        }
    }

    public JoinAdapter(final Config config) {
        this.config = Objects.requireNonNull(config);
    }
}
