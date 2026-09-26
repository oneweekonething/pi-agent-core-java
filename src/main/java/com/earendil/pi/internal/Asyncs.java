package com.earendil.pi.internal;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 异步与参数校验工具：超时竞速、异常解包、前置条件检查。超时用调用方传入的专用调度器实现（而非共享的 orTimeout 内建调度器），保证线程命名与生命周期可控。 */
public final class Asyncs {
    private Asyncs() {}

    public static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
        return value;
    }

    public static String nonBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static void check(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    public static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    public static <T> CompletableFuture<T> withTimeout(
            final CompletableFuture<T> source,
            long timeout,
            TimeUnit unit,
            final ScheduledExecutorService scheduler) {
        require(source, "source");
        require(unit, "unit");
        require(scheduler, "scheduler");
        check(timeout > 0, "timeout must be > 0");

        final CompletableFuture<T> result = new CompletableFuture<>();
        final ScheduledFuture<?> timeoutTask = scheduler.schedule(
                () -> result.completeExceptionally(new TimeoutException("operation timed out")),
                timeout, unit);

        source.whenComplete((value, error) -> {
            timeoutTask.cancel(false);
            if (error == null) result.complete(value);
            else result.completeExceptionally(unwrap(error));
        });
        return result;
    }
}
