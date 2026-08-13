package com.smartlearnly.backend.test.monitor.controller;

import com.smartlearnly.backend.test.monitor.dto.MonitorEvent;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class TestMonitorController {

    /** Chuyển tiếp sự kiện monitor trắc nghiệm đến các subscriber realtime. */
    @MessageMapping("/tests/monitor")
    @SendTo("/topic/tests/monitor")
    public MonitorEvent monitor(MonitorEvent event) {
        event.setType("mcq");
        return event;
    }
}
