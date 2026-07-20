package com.eu.habbo.threading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

public class RejectedExecutionHandlerImpl implements RejectedExecutionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(RejectedExecutionHandlerImpl.class);
    private final RejectedExecutionHandler terminalHandler;

    public RejectedExecutionHandlerImpl() {
        this((runnable, executor) -> {
        });
    }

    private RejectedExecutionHandlerImpl(
            RejectedExecutionHandler terminalHandler) {
        this.terminalHandler = terminalHandler;
    }

    static RejectedExecutionHandlerImpl aborting() {
        return new RejectedExecutionHandlerImpl(
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        try {
            LOGGER.error("{} is rejected", r);
        } finally {
            this.terminalHandler.rejectedExecution(r, executor);
        }
    }
}
