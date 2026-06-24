package de.jotoho.cityrp.hallmonitor;

import com.google.gson.Gson;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.Executors;

public class Main {
    public static void main(final String[] args) throws IOException, InterruptedException {
        if (args.length != 1) {
            System.err.println("Incorrect number of commandline arguments. Pass the path to the bot config, and only the bot config.");
            System.exit(1);
        }
        final Config config = new Gson().fromJson(
                Files.readString(Path.of(args[0]), StandardCharsets.UTF_8),
                Config.class
        );
        final JDA api = JDABuilder.createDefault(config.token())
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .addEventListeners(new JoinAdapter(config))
                .setAutoReconnect(true)
                .setCallbackPool(Executors.newVirtualThreadPerTaskExecutor(), true)
                .setEventPool(Executors.newVirtualThreadPerTaskExecutor(), true)
                .build();

        api.awaitReady();

        final Guild masterGuild = api.getGuildById(config.masterServerID());
        if (Objects.nonNull(masterGuild)) {
            masterGuild.loadMembers().onSuccess(masterMembers -> {
                api.getGuilds()
                        .parallelStream()
                        .filter(guild -> !Objects.equals(guild, masterGuild))
                        .forEach(guild -> {
                            guild.loadMembers(member -> {
                                final boolean isMasterMember = masterMembers.parallelStream()
                                        .map(Member::getUser)
                                        .anyMatch(user -> Objects.equals(user, member.getUser()));
                                if (!isMasterMember && !member.getUser().isBot()) {
                                    member.kick().queue();
                                }
                            });
                        });
            });
        }
        else {
            // Something is very wrong. Shut down immediately.
            System.err.println("FATAL: App not in Master Guild. Shutting down JDA...");
            api.shutdown();
        }
    }
}
