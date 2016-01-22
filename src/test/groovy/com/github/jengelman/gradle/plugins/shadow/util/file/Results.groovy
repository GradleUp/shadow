package com.github.jengelman.gradle.plugins.shadow.util.file

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.ResultHandler

class Results implements ResultHandler<Void> {

    private final Object lock = new Object()

    private boolean success = false
    private GradleConnectionException exception

    void waitForCompletion() {
        synchronized(lock) {
            while(!successful && !failed) {
                lock.wait()
            }
        }
    }

    boolean getSuccessful() {
        return success && !exception
    }

    boolean getFailed() {
        return exception as boolean
    }

    GradleConnectionException getException() {
        return exception
    }

    void markComplete() {
        synchronized(lock) {
            success = true
            exception = null
            lock.notifyAll()
        }
    }

    void markFailed(GradleConnectionException e) {
        synchronized(lock) {
            success = false
            exception = e
            lock.notifyAll()
        }
    }

    @Override
    void onComplete(Void aVoid) {
        markComplete()
    }

    @Override
    void onFailure(GradleConnectionException e) {
        markFailed(e)
    }
}
