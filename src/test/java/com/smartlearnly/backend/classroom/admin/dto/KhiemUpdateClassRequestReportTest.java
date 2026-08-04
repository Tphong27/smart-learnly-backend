package com.smartlearnly.backend.classroom.admin.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KhiemUpdateClassRequestReportTest {

    @Test
    void UTCID_KHIEM_BE_476_hasAnyField_returnsFalseForNewRequest() {
        UpdateClassRequest request = new UpdateClassRequest();

        assertThat(request.hasAnyField()).isFalse();
    }

    @Test
    void UTCID_KHIEM_BE_477_hasAnyField_returnsTrueForExplicitNullField() {
        UpdateClassRequest request = new UpdateClassRequest();
        request.setMeetingUrl(null);

        assertThat(request.hasAnyField()).isTrue();
        assertThat(request.isMeetingUrlProvided()).isTrue();
    }

    @Test
    void UTCID_KHIEM_BE_478_setScheduleDescription_storesValueAndMarksProvided() {
        UpdateClassRequest request = new UpdateClassRequest();

        request.setScheduleDescription("Monday 19:00-21:00");

        assertThat(request.getScheduleDescription()).isEqualTo("Monday 19:00-21:00");
        assertThat(request.isScheduleDescriptionProvided()).isTrue();
    }

    @Test
    void UTCID_KHIEM_BE_479_setScheduleDescription_preservesExplicitNull() {
        UpdateClassRequest request = new UpdateClassRequest();

        request.setScheduleDescription(null);

        assertThat(request.getScheduleDescription()).isNull();
        assertThat(request.isScheduleDescriptionProvided()).isTrue();
        assertThat(request.hasAnyField()).isTrue();
    }

    @Test
    void UTCID_KHIEM_BE_480_isMeetingUrlProvided_distinguishesOmittedAndProvided() {
        UpdateClassRequest request = new UpdateClassRequest();

        assertThat(request.isMeetingUrlProvided()).isFalse();

        request.setMeetingUrl("https://meet.google.com/abc-defg-hij");

        assertThat(request.isMeetingUrlProvided()).isTrue();
        assertThat(request.getMeetingUrl()).isEqualTo("https://meet.google.com/abc-defg-hij");
    }
}
