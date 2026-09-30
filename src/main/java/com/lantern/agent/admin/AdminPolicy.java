package com.lantern.agent.admin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Explicit, opt-in permissions for Lantern Admin Mode.
 */
public final class AdminPolicy {

    private final EnumSet<AdminPermission> permissions;
    private final List<Path> allowedRoots;

    public AdminPolicy(
            EnumSet<AdminPermission> permissions,
            List<Path> allowedRoots
    ) {
        this.permissions =
                permissions.isEmpty()
                        ? EnumSet.noneOf(AdminPermission.class)
                        : EnumSet.copyOf(permissions);

        this.allowedRoots =
                allowedRoots.stream()
                        .map(path ->
                                path.toAbsolutePath()
                                        .normalize()
                        )
                        .toList();
    }

    public boolean allows(
            AdminPermission permission
    ) {
        return permissions.contains(permission);
    }

    public Set<AdminPermission> permissions() {
        return Collections.unmodifiableSet(
                permissions
        );
    }

    public List<Path> allowedRoots() {
        return allowedRoots;
    }

    public static AdminPolicy fromArgs(
            String[] args
    ) {
        EnumSet<AdminPermission> permissions =
                EnumSet.noneOf(AdminPermission.class);

        List<Path> roots =
                new ArrayList<>();

        for (String arg : args) {
            parsePermission(
                    arg,
                    permissions
            );

            parseRoot(
                    arg,
                    roots
            );
        }

        return new AdminPolicy(
                permissions,
                roots
        );
    }

    private static void parsePermission(
            String arg,
            EnumSet<AdminPermission> permissions
    ) {

        if (arg.equals("--admin-files")) {
            permissions.add(
                    AdminPermission.FILESYSTEM
            );
        }

        if (arg.equals("--admin-screen")) {
            permissions.add(
                    AdminPermission.SCREEN
            );
        }

        if (arg.equals("--admin-clipboard")) {
            permissions.add(
                    AdminPermission.CLIPBOARD
            );
        }

        if (arg.equals(
                "--admin-browser-history"
        )) {
            permissions.add(
                    AdminPermission.BROWSER_HISTORY
            );
        }
    }

    private static void parseRoot(
            String arg,
            List<Path> roots
    ) {

        String prefix = "--admin-root=";

        if (!arg.startsWith(prefix)) {
            return;
        }

        Path path =
                Path.of(
                        arg.substring(
                                prefix.length()
                        )
                );

        if (Files.isDirectory(path)) {
            roots.add(path);
        }
    }
}