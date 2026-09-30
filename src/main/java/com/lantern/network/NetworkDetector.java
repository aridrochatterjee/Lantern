package com.lantern.network;

import java.io.*;import java.net.*;import java.nio.file.*;import java.util.*;

public class NetworkDetector {
    public NetworkInterfaceInfo detect() throws Exception {
        Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
        while(interfaces.hasMoreElements()){
            NetworkInterface ni=interfaces.nextElement(); if(!ni.isUp()||ni.isLoopback()||ni.isVirtual())continue;
            for(InterfaceAddress ia:ni.getInterfaceAddresses()){
                if(!(ia.getAddress() instanceof Inet4Address))continue;String ip=ia.getAddress().getHostAddress();int prefix=ia.getNetworkPrefixLength();if(prefix<1||prefix>32)continue;
                return new NetworkInterfaceInfo(ni.getName(),ip,calculateNetwork(ip,prefix),prefix,defaultGateway(),dnsServers());
            }
        }
        throw new IllegalStateException("No active IPv4 network interface found.");
    }
    private String calculateNetwork(String ip,int prefix){long value=ipv4ToLong(ip);long mask=prefix==0?0:(0xFFFFFFFFL<<(32-prefix))&0xFFFFFFFFL;return longToIpv4(value&mask);}
    private String defaultGateway(){String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);try{if(os.contains("win")){for(String l:command("powershell","-NoProfile","-Command","(Get-NetRoute -DestinationPrefix '0.0.0.0/0' | Sort-Object RouteMetric | Select-Object -First 1 -ExpandProperty NextHop)"))if(l.matches("\\d+(\\.\\d+){3}"))return l.trim();}else if(os.contains("mac")){for(String l:command("route","-n","get","default"))if(l.trim().startsWith("gateway:"))return l.substring(l.indexOf(':')+1).trim();}else{for(String l:command("sh","-c","ip route show default 2>/dev/null | head -1")){String[]p=l.trim().split("\\s+");for(int i=0;i<p.length-1;i++)if(p[i].equals("via"))return p[i+1];}}}catch(Exception ignored){}return null;}
    private List<String> dnsServers(){String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);Set<String> out=new LinkedHashSet<>();try{if(os.contains("win")){for(String l:command("powershell","-NoProfile","-Command","Get-DnsClientServerAddress -AddressFamily IPv4 | ForEach-Object {$_.ServerAddresses} | Where-Object {$_}"))if(l.matches("\\d+(\\.\\d+){3}"))out.add(l.trim());}else if(os.contains("mac")){for(String l:command("sh","-c","scutil --dns 2>/dev/null | grep 'nameserver\\[' | head -6")){String[]p=l.trim().split("\\s+");if(p.length>2&&p[2].matches("\\d+(\\.\\d+){3}"))out.add(p[2]);}}else{try{for(String l:Files.readAllLines(Path.of("/etc/resolv.conf"))){l=l.trim();if(l.startsWith("nameserver ")){String v=l.substring(11).trim();if(v.matches("\\d+(\\.\\d+){3}"))out.add(v);}}}catch(Exception ignored){}}}catch(Exception ignored){}return List.copyOf(out);}
    private List<String> command(String...cmd){try{Process p=new ProcessBuilder(cmd).redirectErrorStream(true).start();List<String>o=new ArrayList<>();try(BufferedReader r=p.inputReader()){String l;while((l=r.readLine())!=null)o.add(l);}p.waitFor();return o;}catch(Exception e){return List.of();}}
    public static long ipv4ToLong(String ip){String[]p=ip.split("\\.");long v=0;for(String s:p)v=(v<<8)|Integer.parseInt(s);return v&0xFFFFFFFFL;}
    public static String longToIpv4(long v){return String.format("%d.%d.%d.%d",(v>>>24)&255,(v>>>16)&255,(v>>>8)&255,v&255);}
}
