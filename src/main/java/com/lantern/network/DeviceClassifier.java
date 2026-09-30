package com.lantern.network;

import com.lantern.model.Service;
import java.util.List;
import java.util.Locale;

public class DeviceClassifier {

    public String classify(
            String hostname,
            List<Service> services
    ) {

        String normalizedHostname =
                hostname == null
                        ? ""
                        : hostname.toLowerCase(
                                Locale.ROOT
                        );

        boolean hasSmb =
                hasPort(services, 445)
                        || hasPort(services, 139);

        boolean hasSsh =
                hasPort(services, 22);

        boolean hasWeb =
                hasPort(services, 80)
                        || hasPort(services, 443)
                        || hasPort(services, 8080)
                        || hasPort(services, 8096);

        if (containsAny(
                normalizedHostname,
                "router",
                "gateway",
                "gw"
        )) {
            return "Router/Gateway";
        }

        if (normalizedHostname.contains("nas")
                || (hasSmb && hasSsh)) {

            return "NAS/Server";
        }

        if (containsAny(
                normalizedHostname,
                "printer",
                "print"
        )) {
            return "Printer";
        }

        if (containsAny(
                normalizedHostname,
                "phone",
                "iphone",
                "android"
        )) {
            return "Phone";
        }

        if (containsAny(
                normalizedHostname,
                "tv",
                "roku",
                "chromecast",
                "firetv"
        )) {
            return "TV/Media";
        }

        if (hasSsh && !hasWeb) {
            return "Server/Linux";
        }

        if (hasWeb) {
            return "Web Device";
        }

        if (hasSmb) {
            return "Windows/File Device";
        }

        return "Unknown Device";
    }

    /**
     * Checks whether a device exposes a specific port.
     */
    private boolean hasPort(
            List<Service> services,
            int port
    ) {

        return services.stream()
                .anyMatch(
                        service ->
                                service.port() == port
                );
    }

    /**
     * Checks whether the hostname contains
     * any of the supplied keywords.
     */
    private boolean containsAny(
            String hostname,
            String... keywords
    ) {

        for (String keyword : keywords) {

            if (hostname.contains(keyword)) {
                return true;
            }
        }

        return false;
    }
}