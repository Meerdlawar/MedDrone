package uk.ac.ed.acp.cw2.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MedDispatchRec(
        // Required
        @NotNull
        Integer id,

        // Optional (may be null if not present in JSON)
        LocalDate date,

        // Optional (may be null if not present in JSON)
        //@JsonFormat(pattern = "H:mm")
        @JsonFormat(pattern = "HH:mm:ss")
        LocalTime time,

        // Required object; its own fields will be validated
        @NotNull @Valid
        DispatchRequirements requirements,

        LngLat delivery
) {}