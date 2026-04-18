package com.ohmyproject.session;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

/**
 * 同一会话串行、不同会话完全并发。
 *
 * 使用 Semaphore(1) 而非 ReentrantLock——因为获取锁的线程（Servlet 主线程）和释放锁的线程
 * （chat 虚拟线程）不是同一个，ReentrantLock 在非持有线程释放会抛 IllegalMonitorStateException。
 */
@Component
public class SessionLockRegistry {

    private final ConcurrentHashMap<String, Semaphore> locks = new ConcurrentHashMap<>();

    public Optional<Handle> tryAcquire(String sessionId) {
        Semaphore sem = locks.computeIfAbsent(sessionId, k -> new Semaphore(1));
        if (!sem.tryAcquire()) return Optional.empty();
        return Optional.of(new Handle(sem));
    }

    public static final class Handle implements AutoCloseable {
        private final Semaphore sem;
        private boolean released = false;
        Handle(Semaphore sem) { this.sem = sem; }
        @Override public synchronized void close() {
            if (!released) {
                sem.release();
                released = true;
            }
        }
    }
}
