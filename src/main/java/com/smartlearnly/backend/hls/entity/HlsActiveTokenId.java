package com.smartlearnly.backend.hls.entity;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class HlsActiveTokenId implements Serializable {
    private UUID userId;
    private UUID lessonId;
    private String sessionId;
}
