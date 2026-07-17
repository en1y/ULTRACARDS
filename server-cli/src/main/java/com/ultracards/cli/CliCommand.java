package com.ultracards.cli;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.concurrent.Callable;

abstract class CliCommand implements Callable<Integer> {
    @Spec CommandSpec spec;

    UltracardsAdminCli root() {
        return (UltracardsAdminCli) spec.root().userObject();
    }

    Integer ok(Object value) {
        root().emit(value);
        return 0;
    }
}
