package edu.university.aggregator;

import edu.university.aggregator.cli.AggregatorCli;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        int exitCode = new AggregatorCli().run(args, System.out, System.err);
        if (exitCode != 0) {
            System.exit(exitCode);
        }
    }
}
