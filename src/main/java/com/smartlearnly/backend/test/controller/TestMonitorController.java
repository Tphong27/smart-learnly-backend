package com.smartlearnly.backend.test.controller;

import com.smartlearnly.backend.flashtest.dto.MonitorEvent;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class TestMonitorController {

    @MessageMapping("/tests/monitor")
    @SendTo("/topic/tests/monitor")
    public MonitorEvent monitor(MonitorEvent event) {
        event.setType("mcq");
        return event;
    }
}
