package com.octapoint.core.realtime;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SseHub {

    private final ConcurrentHashMap<String, List<SseEmitter>> emittersByTenant = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String tenantId) {
        SseEmitter emitter = new SseEmitter(0L);
        List<SseEmitter> list = emittersByTenant.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);

        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(ex -> remove.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("tenantId", tenantId, "status", "CONNECTED")));
        } catch (Exception ignored) {
            list.remove(emitter);
        }
        return emitter;
    }

    public void publish(String tenantId, String eventName, Object data) {
        if (tenantId == null) {
            return;
        }
        List<SseEmitter> list = emittersByTenant.get(tenantId);
        if (list == null || list.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                list.remove(emitter);
            }
        }
    }
}
