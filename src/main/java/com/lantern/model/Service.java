package com.lantern.model;

public record Service(int port, String name, String banner) {
    public Service(int port, String name) { this(port, name, null); }
}
