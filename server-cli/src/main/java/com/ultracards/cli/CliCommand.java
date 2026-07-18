package com.ultracards.cli;

import com.ultracards.gateway.dto.admin.AdminPageDTO;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

abstract class CliCommand implements Callable<Integer> {
    @Spec CommandSpec spec;

    UltracardsAdminCli root() {
        return (UltracardsAdminCli) spec.root().userObject();
    }

    Integer ok(Object value) {
        root().emit(value);
        return 0;
    }

    <T> Object pages(int page, int size, boolean all,
                     BiFunction<Integer, Integer, AdminPageDTO<T>> fetch) {
        if (!all) return fetch.apply(page, size);
        var items = new ArrayList<T>();
        var current = page;
        while (true) {
            var result = fetch.apply(current, size);
            items.addAll(result.items());
            if (result.page() + 1 >= result.totalPages()) return items;
            current = result.page() + 1;
        }
    }
}
