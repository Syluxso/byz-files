package com.nyberg.files.events;

import org.springframework.context.ApplicationEvent;

/** Spring event wrapping a {@link FileLifecycleEvent} for AFTER_COMMIT Kafka publish. */
public class FileLifecycleApplicationEvent extends ApplicationEvent {

    private final FileLifecycleEvent payload;

    public FileLifecycleApplicationEvent(Object source, FileLifecycleEvent payload) {
        super(source);
        this.payload = payload;
    }

    public FileLifecycleEvent getPayload() {
        return payload;
    }
}
