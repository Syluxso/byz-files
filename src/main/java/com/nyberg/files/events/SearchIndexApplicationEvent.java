package com.nyberg.files.events;

import org.springframework.context.ApplicationEvent;

public class SearchIndexApplicationEvent extends ApplicationEvent {

    private final SearchIndexEvent payload;

    public SearchIndexApplicationEvent(Object source, SearchIndexEvent payload) {
        super(source);
        this.payload = payload;
    }

    public SearchIndexEvent getPayload() {
        return payload;
    }
}
