package com.lantern.agent.admin;

import java.nio.file.Path;
import java.util.*;

/** Explicit, opt-in permissions for Lantern Admin Mode. */
public final class AdminPolicy {
    private final EnumSet<AdminPermission> permissions;
    private final List<Path> allowedRoots;

    public AdminPolicy(EnumSet<AdminPermission> permissions, List<Path> allowedRoots) {
        this.permissions = permissions.isEmpty() ? EnumSet.noneOf(AdminPermission.class) : EnumSet.copyOf(permissions);
        this.allowedRoots = allowedRoots.stream().map(p -> p.toAbsolutePath().normalize()).toList();
    }

    public boolean allows(AdminPermission permission) { return permissions.contains(permission); }
    public Set<AdminPermission> permissions() { return Collections.unmodifiableSet(permissions); }
    public List<Path> allowedRoots() { return allowedRoots; }

    public static AdminPolicy fromArgs(String[] args) {
        EnumSet<AdminPermission> permissions = EnumSet.noneOf(AdminPermission.class);
        List<Path> roots = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--admin-files")) permissions.add(AdminPermission.FILESYSTEM);
            if (arg.equals("--admin-screen")) permissions.add(AdminPermission.SCREEN);
            if (arg.equals("--admin-clipboard")) permissions.add(AdminPermission.CLIPBOARD);
            if (arg.equals("--admin-browser-history")) permissions.add(AdminPermission.BROWSER_HISTORY);
            if (arg.startsWith("--admin-root=")) {
                Path p = Path.of(arg.substring("--admin-root=".length()));
                if (java.nio.file.Files.isDirectory(p)) roots.add(p);
            }
        }
        return new AdminPolicy(permissions, roots);
    }
}
