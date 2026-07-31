package com.timbernest.admin.importing;

import java.util.ArrayList;
import java.util.List;

public record ImportResult(String source, String message, Long machineId,
                           int machinesCreated, int toolsCreated, List<String> warnings) {
    public static ImportResult of(String source, String message, Long machineId,
                                  int machines, int tools) {
        return new ImportResult(source, message, machineId, machines, tools, new ArrayList<>());
    }
}
