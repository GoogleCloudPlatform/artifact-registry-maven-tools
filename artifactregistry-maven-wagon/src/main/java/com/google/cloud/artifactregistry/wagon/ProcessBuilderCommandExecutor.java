package com.google.cloud.artifactregistry.wagon;

import com.google.api.client.util.GenericData;
import com.google.auth.oauth2.AccessToken;
import com.google.cloud.artifactregistry.auth.CommandExecutor;
import com.google.cloud.artifactregistry.auth.CommandExecutorResult;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ProcessBuilderCommandExecutor implements CommandExecutor {
    @Override
    public CommandExecutorResult executeCommand(String command, String... args) throws IOException {
        List<String> argList = new ArrayList<>();
        argList.add(command);
        argList.addAll(Arrays.asList(args));

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(argList);
        Process process = processBuilder.start();

        try {
            int exitCode = process.waitFor();
            String stdOut = readStreamToString(process.getInputStream());
            String stdErr = readStreamToString(process.getErrorStream());

            return new CommandExecutorResult(exitCode, stdOut, stdErr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    // Reads a stream to a string, this code is basically copied from 'copyReaderToBuilder' from
    // com.google.io.CharStreams in the Guava library.
    private static String readStreamToString(InputStream input) throws IOException {
        InputStreamReader reader = new InputStreamReader(input);
        StringBuilder output = new StringBuilder();
        char[] buf = new char[0x800];
        int nRead;
        while ((nRead = reader.read(buf)) != -1) {
            output.append(buf, 0, nRead);
        }
        return output.toString();
    }
}
