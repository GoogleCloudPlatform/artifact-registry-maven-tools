package com.google.cloud.artifactregistry.gradle.plugin;

import com.google.cloud.artifactregistry.auth.CommandExecutor;
import com.google.cloud.artifactregistry.auth.CommandExecutorResult;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.process.ExecOutput;
import org.gradle.process.internal.ExecException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProviderFactoryCommandExecutor implements CommandExecutor {
    private final ProviderFactory providerFactory;

    public ProviderFactoryCommandExecutor(ProviderFactory providerFactory) {
        this.providerFactory = providerFactory;
    }

    @Override
    public CommandExecutorResult executeCommand(String command, String... args) throws IOException {
        List<String> argList = new ArrayList<>();
        argList.add(command);
        argList.addAll(Arrays.asList(args));

        ExecOutput execOutput;
        try {
            execOutput = providerFactory.exec(execSpec -> {
                execSpec.commandLine(argList);
            });
        } catch (ExecException e) {
            // Downstream cannot handle Gradle specific exceptions
            throw new IOException(e);
        }

        int exitCode = execOutput.getResult().get().getExitValue();
        String stdOut = execOutput.getStandardOutput().getAsText().get();
        String stdErr = execOutput.getStandardError().getAsText().get();

        return new CommandExecutorResult(
                exitCode,
                stdOut,
                stdErr
        );
    }
}
