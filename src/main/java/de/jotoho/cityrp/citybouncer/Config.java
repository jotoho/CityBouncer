package de.jotoho.cityrp.citybouncer;

import java.util.Map;
import java.util.Objects;

public record Config(String parentServerID, String token, Map<String, ServerConfig> servers) {
    public static final ServerConfig DEFAULT = new ServerConfig(
            false,
            false,
            false,
            false
    );

    public record ServerConfig(
            boolean disableKickOnJoin,
            boolean disableKickOnLeave,
            boolean disableKickExisting,
            boolean disableBanForwarding
    ) {

    }

    public ServerConfig getServerConfig(final String serverID) {
        Objects.requireNonNull(serverID);
        return this.servers().getOrDefault(serverID, Config.DEFAULT);
    }
}
