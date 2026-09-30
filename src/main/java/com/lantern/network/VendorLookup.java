package com.lantern.network;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public class VendorLookup {
    private final Map<String,String> oui = new HashMap<>();
    private static final Pattern MAC_PREFIX = Pattern.compile("(?i)^([0-9A-F]{2}[:-][0-9A-F]{2}[:-][0-9A-F]{2})");
    public VendorLookup(String explicitPath) {
        List<Path> paths = new ArrayList<>();
        if (explicitPath != null) paths.add(Paths.get(explicitPath));
        paths.add(Paths.get("data/oui.txt"));
        paths.add(Paths.get("/usr/share/ieee-data/oui.txt"));
        paths.add(Paths.get("/usr/share/ieee-data/oui.txt.gz"));
        for (Path p : paths) if (Files.isRegularFile(p) && !p.toString().endsWith(".gz")) { load(p); break; }
    }
    private void load(Path path) {
        try (BufferedReader r=Files.newBufferedReader(path)) {
            String line;
            while((line=r.readLine())!=null) {
                Matcher m=MAC_PREFIX.matcher(line.trim());
                if(!m.find()) continue;
                int end=line.indexOf("(base 16)");
                if(end<0) end=line.indexOf("#");
                String vendor=end>0?line.substring(end+10).trim():line.substring(m.end()).trim();
                if(vendor.isBlank()) continue;
                oui.put(m.group(1).replace(':','-').toUpperCase(), vendor);
            }
        } catch(IOException ignored) {}
    }
    public String lookup(String mac) {
        if(mac==null || mac.length()<8) return "Unknown";
        String key=mac.substring(0,8).replace(':','-').toUpperCase();
        return oui.getOrDefault(key,"Unknown");
    }
}
