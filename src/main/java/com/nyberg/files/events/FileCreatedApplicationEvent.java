package com.nyberg.files.events;

import org.springframework.context.ApplicationEvent;

public class FileCreatedApplicationEvent extends ApplicationEvent {

    private final FileLifecycleEvent payload;

    public FileCreatedApplicationEvent(Object source, FileLifecycleEvent payload) {
        super(source);
        this.payload = payload;
    }

    public FileLifecycleEvent getPayload() {
        return payload;
    }
}
