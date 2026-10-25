package com.morpheus.cli.command;

import java.io.PrintStream;
import java.util.Map;
import java.util.Properties;

/** {@code paths}: the data, configuration, log and database locations the layout resolves for these arguments. */
public final class MorpheusPathsCli {
    private static final String COMMAND = "paths";

    public static boolean handles(String[] args) {
        return GlobalArgs.command(args).equals(COMMAND);
    }

    public int run(
            String[] args,
            PrintStream out,
            PrintStream err,
            Map<String, String> environment,
            Properties properties) {
        return CliCommandRun.run(args, out, err, environment, properties, (tokens, run) -> paths(run));
    }

    private static int paths(CliCommandRun run) {
        PathsView view = new PathsView(
                run.layout().dataDirectory().toString(),
                run.layout().configDirectory().toString(),
                run.layout().logsDirectory().toString(),
                run.databasePath().toString());
        run.print(view, String.join(System.lineSeparator(),
                "data=" + view.dataDirectory(),
                "config=" + view.configDirectory(),
                "logs=" + view.logsDirectory(),
                "database=" + view.databasePath()));
        return CliExitCode.SUCCESS.code();
    }

    private record PathsView(String dataDirectory, String configDirectory, String logsDirectory, String databasePath) {}
}
