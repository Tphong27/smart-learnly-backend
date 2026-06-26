package com.smartlearnly.backend.assignment.controller;

import com.smartlearnly.backend.flashtest.dto.MonitorEvent;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class AssignmentMonitorController {

    @MessageMapping("/assignments/monitor")
    @SendTo("/topic/assignments/monitor")
    public MonitorEvent monitor(MonitorEvent event) {
        event.setType("essay");
        return event;
    }
}
