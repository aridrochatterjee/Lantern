package com.lantern.history;

import com.lantern.model.Device;

public record NetworkEvent(String type, Device device) {}
