package fr.kdns.weiyun.dormmonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.BiConsumer;

/** Single-flight requests with a wall-clock deadline, including executor queue time. */
final class RefreshDispatcher<T> {
    private final ExecutorService workers;
    private final ScheduledExecutorService timer;
    private final Executor delivery;
    private Flight current;

    private final class Listener {
        final BiConsumer<T, Exception> callback;
        boolean cancelled;
        Listener(BiConsumer<T, Exception> callback) { this.callback = callback; }
    }
    private final class Flight {
        final List<Listener> listeners = new ArrayList<>();
        FutureTask<Void> work;
        ScheduledFuture<?> deadline;
        boolean done;
    }
    RefreshDispatcher(ExecutorService workers, ScheduledExecutorService timer, Executor delivery) {
        this.workers = workers;
        this.timer = timer;
        this.delivery = delivery;
    }
    synchronized Runnable request(Callable<T> request, long timeoutMs, BiConsumer<T, Exception> callback) {
        Listener listener = new Listener(callback);
        Flight flight = current;
        if (flight == null) {
            flight = new Flight();
            current = flight;
            final Flight created = flight;
            flight.listeners.add(listener);
            flight.work = new FutureTask<>(() -> {
                try { finish(created, request.call(), null); }
                catch (Exception e) { finish(created, null, e); }
                return null;
            });
            flight.deadline = timer.schedule(
                    () -> finish(created, null, new TimeoutException("refresh timed out")),
                    timeoutMs, TimeUnit.MILLISECONDS);
            workers.execute(flight.work);
        } else flight.listeners.add(listener);
        final Flight subscribed = flight;
        return () -> {
            synchronized (RefreshDispatcher.this) {
                listener.cancelled = true;
                if (subscribed.listeners.stream().allMatch(l -> l.cancelled)) {
                    subscribed.done = true;
                    if (current == subscribed) current = null;
                    subscribed.work.cancel(true);
                    subscribed.deadline.cancel(false);
                }
            }
        };
    }
    private void finish(Flight flight, T result, Exception error) {
        synchronized (this) {
            if (flight.done) return;
            flight.done = true;
            flight.deadline.cancel(false);
            if (error instanceof TimeoutException) flight.work.cancel(true);
        }
        delivery.execute(() -> {
            List<Listener> listeners;
            synchronized (RefreshDispatcher.this) {
                if (current == flight) current = null;
                listeners = new ArrayList<>(flight.listeners);
            }
            for (Listener listener : listeners) {
                synchronized (RefreshDispatcher.this) {
                    if (listener.cancelled) continue;
                }
                try { listener.callback.accept(result, error); }
                catch (RuntimeException ignored) { /* Other listeners must still finish. */ }
            }
        });
    }
}
