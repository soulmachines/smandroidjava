// Copyright 2022 Soul Machines Ltd

package com.soulmachines.smandroidjava;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.soulmachines.android.smsdk.core.scene.Content;
import com.soulmachines.android.smsdk.core.scene.Rect;

import java.util.Map;

public class ContentImpl implements Content {
    static int uniqueId = 1;

    private final String id = "object-" + Integer.toString(uniqueId++);
    private final Rect bounds;

    public ContentImpl(Rect r) {
        this.bounds = r;
    }

    @NonNull
    @Override
    public String getId() {
        return id;
    }

    @Nullable
    @Override
    public Map<String, Object> getMeta() {
        return null;
    }

    @NonNull
    @Override
    public Rect getRect() {
        return bounds;
    }
}
