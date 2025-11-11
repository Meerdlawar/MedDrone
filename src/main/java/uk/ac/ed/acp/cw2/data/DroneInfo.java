package uk.ac.ed.acp.cw2.data;

import jakarta.validation.constraints.NotNull;
import org.aspectj.weaver.ast.Not;

public record DroneInfo (
        @NotNull
        String name,
        @NotNull
        int id,
        @NotNull
        DroneCapability capability
) {}
