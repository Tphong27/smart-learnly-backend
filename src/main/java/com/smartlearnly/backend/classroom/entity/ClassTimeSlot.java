package com.smartlearnly.backend.classroom.entity;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

public enum ClassTimeSlot {

    SLOT_1(
            "Slot 1",
            LocalTime.of(7, 30),
            LocalTime.of(9, 30)),

    SLOT_2(
            "Slot 2",
            LocalTime.of(9, 45),
            LocalTime.of(11, 45)),

    SLOT_3(
            "Slot 3",
            LocalTime.of(13, 0),
            LocalTime.of(15, 0)),

    SLOT_4(
            "Slot 4",
            LocalTime.of(15, 15),
            LocalTime.of(17, 15)),

    SLOT_5(
            "Slot 5",
            LocalTime.of(19, 30),
            LocalTime.of(21, 30)),

    SLOT_6(
            "Slot 6",
            LocalTime.of(21, 45),
            LocalTime.of(23, 45));

    private final String label;
    private final LocalTime startTime;
    private final LocalTime endTime;

    ClassTimeSlot(
            String label,
            LocalTime startTime,
            LocalTime endTime) {
        this.label = label;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getLabel() {
        return label;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public static Optional<ClassTimeSlot> find(
            LocalTime startTime,
            LocalTime endTime) {
        return Arrays.stream(values())
                .filter(slot -> slot.startTime.equals(startTime)
                        && slot.endTime.equals(endTime))
                .findFirst();
    }

    public static String allowedSlotsDescription() {
        return Arrays.stream(values())
                .map(slot -> "%s %s-%s".formatted(
                        slot.label,
                        slot.startTime,
                        slot.endTime))
                .collect(Collectors.joining(", "));
    }
}