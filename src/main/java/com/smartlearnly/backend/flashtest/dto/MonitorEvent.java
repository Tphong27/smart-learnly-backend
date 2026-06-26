package com.smartlearnly.backend.flashtest.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MonitorEvent {
    private UUID targetId;
    private UUID attemptId;
    private UUID submissionId;
    private UUID studentId;
    private String studentName;
    private String type;
    private String status;
    private Instant startTime;
    private Instant endTime;
    private Long remainingSeconds;
    private BigDecimal score;
    private BigDecimal percentage;
    private String fileUrl;
    private String fileName;
}
