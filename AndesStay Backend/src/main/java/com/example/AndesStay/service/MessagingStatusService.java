package com.example.AndesStay.service;

import com.example.AndesStay.dto.MessagingStatusResponse;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import java.time.Clock;

@Service
public class MessagingStatusService {
    private final AmqpAdmin admin;
    private final Queue reception;
    private final Queue guest;
    private final Queue dead;
    private final ObjectProvider<MessagingDemoService> demo;
    private final Clock clock;

    public MessagingStatusService(AmqpAdmin admin, @Qualifier("reservationQueue") Queue reception,
            @Qualifier("guestNotificationQueue") Queue guest, @Qualifier("reservationDeadLetterQueue") Queue dead,
            ObjectProvider<MessagingDemoService> demo, Clock clock) {
        this.admin=admin; this.reception=reception; this.guest=guest; this.dead=dead; this.demo=demo; this.clock=clock;
    }
    public MessagingStatusResponse status() {
        var localDemo=demo.getIfAvailable();
        boolean demoEnabled=localDemo!=null;
        try {
            QueueInformation receptionInfo=admin.getQueueInfo(reception.getName());
            QueueInformation guestInfo=admin.getQueueInfo(guest.getName());
            QueueInformation deadInfo=admin.getQueueInfo(dead.getName());
            QueueInformation demoInfo=demoEnabled ? admin.getQueueInfo(localDemo.deadQueueName()) : null;
            if(receptionInfo==null || guestInfo==null || deadInfo==null || (demoEnabled && demoInfo==null))
                return unavailable(demoEnabled);
            return new MessagingStatusResponse(true, receptionInfo.getMessageCount()+guestInfo.getMessageCount(),
                deadInfo.getMessageCount(), (long)receptionInfo.getConsumerCount(), (long)guestInfo.getConsumerCount(),
                demoEnabled, demoEnabled ? demoInfo.getMessageCount() : null, clock.instant());
        } catch(AmqpException error) { return unavailable(demoEnabled); }
    }
    private MessagingStatusResponse unavailable(boolean demoEnabled) {
        return new MessagingStatusResponse(false, null, null, null, null, demoEnabled, null, clock.instant());
    }
}
