package com.smartlearnly.backend.classroom.schedule.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScheduleDescriptionParserTest {

    private ScheduleDescriptionParser parser;

    @BeforeEach
    void setUp() {
        parser = new ScheduleDescriptionParser(new ObjectMapper());
    }

    @Test
    void parse_validSchedule_returnsCorrectMapping() {
        String json = """
            [
              {"dayOfWeek": "MONDAY", "slots": [{"startTime": "07:30", "endTime": "09:30"}]},
              {"dayOfWeek": "WEDNESDAY", "slots": [{"startTime": "19:30", "endTime": "21:30"}]}
            ]
            """;

        Map<DayOfWeek, List<ScheduleDescriptionParser.TimeRange>> result = parser.parse(json);

        assertThat(result).hasSize(2);
        assertThat(result.get(DayOfWeek.MONDAY)).hasSize(1);
        assertThat(result.get(DayOfWeek.WEDNESDAY)).hasSize(1);

        ScheduleDescriptionParser.TimeRange mondayRange = result.get(DayOfWeek.MONDAY).get(0);
        assertThat(mondayRange.startTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(mondayRange.endTime()).isEqualTo(LocalTime.of(9, 30));
    }

    @Test
    void parse_multipleSlotsSameDay_returnsAllSlots() {
        String json = """
            [
              {
                "dayOfWeek": "SATURDAY",
                "slots": [
                  {"startTime": "07:30", "endTime": "09:30"},
                  {"startTime": "09:45", "endTime": "11:45"}
                ]
              }
            ]
            """;

        Map<DayOfWeek, List<ScheduleDescriptionParser.TimeRange>> result = parser.parse(json);

        assertThat(result.get(DayOfWeek.SATURDAY)).hasSize(2);
    }

    @Test
    void parse_nullDescription_throwsException() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("Class schedule is required");
    }

    @Test
    void parse_blankDescription_throwsException() {
        assertThatThrownBy(() -> parser.parse("   "))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("Class schedule is required");
    }

    @Test
    void parse_invalidJson_throwsException() {
        assertThatThrownBy(() -> parser.parse("not json"))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("valid JSON");
    }

    @Test
    void parse_nonArrayJson_throwsException() {
        assertThatThrownBy(() -> parser.parse("{\"day\": \"MONDAY\"}"))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("JSON array");
    }

    @Test
    void parse_emptyArray_throwsException() {
        assertThatThrownBy(() -> parser.parse("[]"))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("at least one class schedule");
    }

    @Test
    void parse_dayNotObject_throwsException() {
        assertThatThrownBy(() -> parser.parse("[\"MONDAY\"]"))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    void parse_invalidDayOfWeek_throwsException() {
        String json = """
            [{"dayOfWeek": "INVALID_DAY", "slots": [{"startTime": "07:30", "endTime": "09:30"}]}]
            """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("Invalid schedule day");
    }

    @Test
    void parse_duplicateDay_throwsException() {
        String json = """
            [
              {"dayOfWeek": "MONDAY", "slots": [{"startTime": "07:30", "endTime": "09:30"}]},
              {"dayOfWeek": "MONDAY", "slots": [{"startTime": "19:30", "endTime": "21:30"}]}
            ]
            """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("duplicate day");
    }

    @Test
    void parse_slotsNotArray_throwsException() {
        String json = """
            [{"dayOfWeek": "MONDAY", "slots": "07:30-09:30"}]
            """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("slots must be an array");
    }

    @Test
    void parse_emptySlots_throwsException() {
        String json = """
            [{"dayOfWeek": "MONDAY", "slots": []}]
            """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("at least one time slot");
    }

    @Test
    void parse_invalidTimeFormat_throwsException() {
        String json = """
            [{"dayOfWeek": "MONDAY", "slots": [{"startTime": "7:30", "endTime": "09:30"}]}]
            """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("HH:mm format");
    }

    @Test
    void parse_invalidTimeRange_throwsException() {
        String json = """
            [{"dayOfWeek": "MONDAY", "slots": [{"startTime": "07:30", "endTime": "08:30"}]}]
            """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("may only use");
    }

    @Test
    void parse_duplicateSlotSameDay_throwsException() {
        String json = """
            [
              {
                "dayOfWeek": "MONDAY",
                "slots": [
                  {"startTime": "07:30", "endTime": "09:30"},
                  {"startTime": "07:30", "endTime": "09:30"}
                ]
              }
            ]
            """;

        assertThatThrownBy(() -> parser.parse(json))
                .isInstanceOf(ScheduleParseException.class)
                .hasMessageContaining("selected more than once");
    }

    @Test
    void parse_allValidSlots_parsesSuccessfully() {
        String json = """
            [
              {"dayOfWeek": "MONDAY", "slots": [{"startTime": "07:30", "endTime": "09:30"}]},
              {"dayOfWeek": "TUESDAY", "slots": [{"startTime": "09:45", "endTime": "11:45"}]},
              {"dayOfWeek": "WEDNESDAY", "slots": [{"startTime": "13:00", "endTime": "15:00"}]},
              {"dayOfWeek": "THURSDAY", "slots": [{"startTime": "15:15", "endTime": "17:15"}]},
              {"dayOfWeek": "FRIDAY", "slots": [{"startTime": "19:30", "endTime": "21:30"}]},
              {"dayOfWeek": "SATURDAY", "slots": [{"startTime": "21:45", "endTime": "23:45"}]}
            ]
            """;

        Map<DayOfWeek, List<ScheduleDescriptionParser.TimeRange>> result = parser.parse(json);

        assertThat(result).hasSize(6);
        assertThat(result.get(DayOfWeek.MONDAY).get(0).startTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(result.get(DayOfWeek.TUESDAY).get(0).startTime()).isEqualTo(LocalTime.of(9, 45));
        assertThat(result.get(DayOfWeek.WEDNESDAY).get(0).startTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(result.get(DayOfWeek.THURSDAY).get(0).startTime()).isEqualTo(LocalTime.of(15, 15));
        assertThat(result.get(DayOfWeek.FRIDAY).get(0).startTime()).isEqualTo(LocalTime.of(19, 30));
        assertThat(result.get(DayOfWeek.SATURDAY).get(0).startTime()).isEqualTo(LocalTime.of(21, 45));
    }
}
