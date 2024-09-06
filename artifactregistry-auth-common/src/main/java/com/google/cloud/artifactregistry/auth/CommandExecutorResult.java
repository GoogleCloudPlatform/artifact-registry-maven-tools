package com.google.cloud.artifactregistry.auth;

public class CommandExecutorResult {
    public final int exitCode;
    public final String stdOut;
    public final String stdErr;

    public CommandExecutorResult(
            int exitCode,
            String stdOut,
            String stdErr
    ) {
        this.exitCode = exitCode;
        this.stdOut = stdOut;
        this.stdErr = stdErr;
    }
}
