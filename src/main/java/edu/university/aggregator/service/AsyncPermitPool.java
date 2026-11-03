package edu.university.aggregator.service;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

final class AsyncPermitPool {
    private int available;
    private final Queue<CompletableFuture<Permit>> waiters = new ArrayDeque<>();

    AsyncPermitPool(int permits) {
        if (permits < 1) {
            throw new IllegalArgumentException("permits must be positive");
        }
        available = permits;
    }

    synchronized CompletableFuture<Permit> acquire() {
        if (available > 0) {
            available--;
            return CompletableFuture.completedFuture(new Permit(this));
        }
        CompletableFuture<Permit> waiter = new CompletableFuture<>();
        waiters.add(waiter);
        return waiter;
    }

    private synchronized void release() {
        CompletableFuture<Permit> waiter = waiters.poll();
        if (waiter == null) {
            available++;
        } else {
            waiter.complete(new Permit(this));
        }
    }

    static final class Permit implements AutoCloseable {
        private final AsyncPermitPool owner;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(AsyncPermitPool owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (released.compareAndSet(false, true)) {
                owner.release();
            }
        }
    }
}
