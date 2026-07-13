package de.jotoho.cityrp.citybouncer;

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
        final JDA api = JDABuilder.createLight(config.token())
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MODERATION)
                .addEventListeners(new JoinAdapter(config))
                .setAutoReconnect(true)
                .setCallbackPool(Executors.newVirtualThreadPerTaskExecutor(), true)
                .setEventPool(Executors.newVirtualThreadPerTaskExecutor(), true)
                .build();
    }
}
