package com.lantern.network;

import com.lantern.model.Device;
import com.lantern.model.Service;
import java.util.Locale;

public class DeviceClassifier {
    public String classify(String hostname, java.util.List<Service> services) {
        String h = hostname == null ? "" : hostname.toLowerCase(Locale.ROOT);
        boolean smb = has(services,445) || has(services,139);
        boolean ssh = has(services,22);
        boolean web = has(services,80) || has(services,443) || has(services,8080) || has(services,8096);
        if (h.contains("router") || h.contains("gateway") || h.contains("gw")) return "Router/Gateway";
        if (h.contains("nas") || (smb && ssh)) return "NAS/Server";
        if (h.contains("printer") || h.contains("print")) return "Printer";
        if (h.contains("phone") || h.contains("iphone") || h.contains("android")) return "Phone";
        if (h.contains("tv") || h.contains("roku") || h.contains("chromecast") || h.contains("firetv")) return "TV/Media";
        if (ssh && !web) return "Server/Linux";
        if (web) return "Web Device";
        if (smb) return "Windows/File Device";
        return "Unknown Device";
    }
    private boolean has(java.util.List<Service> s,int p){return s.stream().anyMatch(x->x.port()==p);}
}
