/**
 * Copyright (c) 2017 Dell Inc., or its subsidiaries. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.pravega.test.common;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Simple count-based error injector.
 */
public class ErrorInjector<T extends Throwable> {
    private final Predicate<Integer> countTrigger;
    private final Supplier<T> errorGenerator;
    private final AtomicInteger count;
    private T lastCycleException;

    /**
     * Creates a new instance of the ErrorInjector class.
     *
     * @param countTrigger   A Predicate that will be used to determine whether to throw the exception. The argument to
     *                       the predicate is the number of times the throwIfNecessary() method was invoked.
     * @param errorGenerator A Supplier that creates Exceptions of type T, when invoked.
     */
    public ErrorInjector(Predicate<Integer> countTrigger, Supplier<T> errorGenerator) {
        this.countTrigger = countTrigger;
        this.errorGenerator = errorGenerator;
        this.count = new AtomicInteger();
    }

    /**
     * Throws an exception of type T if the count trigger activates.
     *
     * @throws T If necessary to throw the exception.
     */
    public void throwIfNecessary() throws T {
        T ex = generateExceptionIfNecessary();
        if (ex != null) {
            throw ex;
        }
    }

    /**
     * If the given error injector generates an exception, that exception will be thrown (Wrapped as a CompletionException).
     * If the given error injector is null or does not generate an exception, nothing will happen.
     *
     * @param injector The Error Injector to use.
     * @param <T>      The type of exception to throw.
     */
    public static <T extends Throwable> void throwSyncExceptionIfNeeded(ErrorInjector<T> injector) {
        if (injector != null) {
            T ex = injector.generateExceptionIfNecessary();
            if (ex != null) {
                throw new CompletionException(ex);
            }
        }
    }

    /**
     * Returns a CompletableFuture that, if the given injector generates a non-null exception, will be completed exceptionally.
     * If the given injector is null or does not generate an exception (null), this method returns the future generated
     * byt the given supplier.
     *
     * @param injector      The Error Injector to use.
     * @param successFuture A Supplier that generates the future to return if no exception happened.
     * @param <ExceptionT>  The type of exception to throw.
     * @param <ReturnT> The type of the return value.
     */
    public static <ReturnT, ExceptionT extends Throwable> CompletableFuture<ReturnT> throwAsyncExceptionIfNeeded(
            ErrorInjector<ExceptionT> injector, Supplier<CompletableFuture<ReturnT>> successFuture) {
        CompletableFuture<ReturnT> result = null;
        if (injector != null) {
            ExceptionT ex = injector.generateExceptionIfNecessary();
            if (ex != null) {
                result = new CompletableFuture<>();
                result.completeExceptionally(ex);
            }
        }

        return result != null ? result : successFuture.get();
    }

    /**
     * Gets a value indicating the Exception (T) that was thrown during the last call to throwIfNecessary(). If no
     * exception was thrown, null is returned.
     */
    public T getLastCycleException() {
        return this.lastCycleException;
    }

    /**
     * Generates an exception of type T if the count trigger activates.
     *
     * @throws T
     */
    private T generateExceptionIfNecessary() {
        this.lastCycleException = null;
        if (countTrigger.test(this.count.getAndIncrement())) {
            this.lastCycleException = errorGenerator.get();
            assert this.lastCycleException != null;
            return this.lastCycleException;
        }

        return null;
    }
}
