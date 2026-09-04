package com.docquery.document.sse;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 单次 SSE 连接：心跳防网关掐空闲、只关闭一次。
 */
public class AskSseSession {

    private static final ScheduledExecutorService HEARTBEAT_POOL = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "docquery-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ScheduledFuture<?> heartbeat;

    public AskSseSession(SseEmitter emitter, long heartbeatMs) {
        this.emitter = emitter;
        this.heartbeat = HEARTBEAT_POOL.scheduleAtFixedRate(this::ping, heartbeatMs, heartbeatMs, TimeUnit.MILLISECONDS);
        this.emitter.onCompletion(this::stopHeartbeat);
        this.emitter.onTimeout(() -> fail("问答超时，请稍后重试或转人工"));
        this.emitter.onError(error -> closeQuietly());
    }

    public void send(String event, Object data) {
        if (closed.get()) {
            return;
        }
        synchronized (this) {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event).data(data));
            } catch (IOException e) {
                closeQuietly();
            }
        }
    }

    public void complete() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        stopHeartbeat();
        try {
            emitter.send(SseEmitter.event().name("done").data("ok"));
        } catch (IOException ignored) {
            // 客户端已断开时仍 complete
        }
        emitter.complete();
    }

    public void fail(String message) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        stopHeartbeat();
        try {
            emitter.send(SseEmitter.event().name("error").data(message));
        } catch (IOException ignored) {
            // ignore
        }
        emitter.complete();
    }

    private void ping() {
        if (closed.get()) {
            return;
        }
        synchronized (this) {
            if (closed.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (IOException e) {
                closeQuietly();
            }
        }
    }

    private void closeQuietly() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        stopHeartbeat();
        emitter.complete();
    }

    private void stopHeartbeat() {
        heartbeat.cancel(false);
    }
}
