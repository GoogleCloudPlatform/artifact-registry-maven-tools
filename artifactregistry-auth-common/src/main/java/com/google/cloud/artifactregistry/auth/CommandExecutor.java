package com.google.cloud.artifactregistry.auth;

import java.io.IOException;

public interface CommandExecutor {
    public CommandExecutorResult executeCommand(
        String command,
        String... args
    ) throws IOException;
}
