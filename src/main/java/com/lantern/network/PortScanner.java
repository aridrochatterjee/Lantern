package com.lantern.network;

import com.lantern.model.Service;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class PortScanner {
    public List<Service> scan(String ip, int[] ports, int timeoutMs) {
        if (ports == null || ports.length == 0) return List.of();
        ExecutorService pool=Executors.newFixedThreadPool(Math.min(20,Math.max(1,ports.length)));
        try {
            List<Future<Service>> futures=new ArrayList<>(); for(int port:ports) futures.add(pool.submit(()->check(ip,port,timeoutMs)));
            List<Service> found=new ArrayList<>(); for(Future<Service> f:futures)try{Service s=f.get();if(s!=null)found.add(s);}catch(ExecutionException ignored){}
            found.sort(Comparator.comparingInt(Service::port)); return found;
        }catch(InterruptedException e){Thread.currentThread().interrupt();return List.of();}finally{pool.shutdownNow();}
    }
    private Service check(String ip,int port,int timeout){
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress(ip,port),timeout); socket.setSoTimeout(Math.min(500,timeout));
            String banner=probeBanner(socket,port);
            return new Service(port,name(port),banner);
        }catch(Exception e){return null;}
    }
    private String probeBanner(Socket socket,int port){
        try{
            if(port==80||port==8000||port==8080||port==8096||port==3000||port==5000||port==8443){
                String req="HEAD / HTTP/1.0\r\nHost: lantern.local\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(req.getBytes(StandardCharsets.US_ASCII)); socket.getOutputStream().flush();
            }
            byte[] b=new byte[1024]; int n=socket.getInputStream().read(b); if(n<=0)return null;
            String s=new String(b,0,n,StandardCharsets.ISO_8859_1).replaceAll("[\\r\\n]+"," | ").trim();
            return s.length()>300?s.substring(0,300):s;
        }catch(Exception e){return null;}
    }
    private String name(int p){return switch(p){case 20->"FTP-DATA";case 21->"FTP";case 22->"SSH";case 23->"Telnet";case 25->"SMTP";case 53->"DNS";case 67,68->"DHCP";case 80->"HTTP";case 110->"POP3";case 123->"NTP";case 135->"MS-RPC";case 139->"NetBIOS";case 143->"IMAP";case 443->"HTTPS";case 445->"SMB";case 631->"IPP";case 1433->"MSSQL";case 1883->"MQTT";case 3000->"HTTP-ALT";case 3306->"MySQL";case 3389->"RDP";case 5000->"HTTP-ALT";case 5432->"PostgreSQL";case 5900->"VNC";case 6379->"Redis";case 8000,8080->"HTTP-ALT";case 8443->"HTTPS-ALT";case 8096->"Jellyfin";default->"TCP";};}
}
