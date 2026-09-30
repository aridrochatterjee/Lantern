package com.lantern.network;

import com.lantern.model.Device;
import com.lantern.model.Service;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.*;

public class DeviceScanner {
    private final int[] ports;
    private final PingService ping = new PingService();
    private final MacAddressService mac = new MacAddressService();
    private final PortScanner portsScanner = new PortScanner();
    private final VendorLookup vendorLookup;
    private final DeviceClassifier classifier = new DeviceClassifier();
    public DeviceScanner(int[] ports, String ouiPath) { this.ports=ports; this.vendorLookup=new VendorLookup(ouiPath); }

    public List<Device> scan(NetworkInterfaceInfo network, ScanListener listener) throws InterruptedException {
        List<String> addresses=hosts(network.getNetworkAddress(),network.getPrefixLength());
        ExecutorService pool=Executors.newFixedThreadPool(Math.min(64,Math.max(4,addresses.size())));
        try {
            List<Future<Device>> futures=new ArrayList<>();
            for(String ip:addresses) futures.add(pool.submit(()->inspect(ip)));
            List<Device> found=new ArrayList<>(); int done=0;
            for(Future<Device> f:futures){
                try{Device d=f.get(); if(d!=null) found.add(d);}catch(ExecutionException ignored){}
                done++; if(listener!=null) listener.progress(done,addresses.size());
            }
            found.sort(Comparator.comparing(Device::getIpAddress,DeviceScanner::compareIp));
            return found;
        } finally { pool.shutdownNow(); }
    }
    public List<Device> scan(NetworkInterfaceInfo network) throws InterruptedException { return scan(network,null); }

    private Device inspect(String ip){
        long latency=ping.measure(ip,1000);
        String macAddress=mac.getMacAddress(ip);
        List<Service> services=portsScanner.scan(ip,ports,250);
        if(latency<0 && macAddress==null && services.isEmpty()) return null;
        String hostname=resolve(ip);
        String vendor=vendorLookup.lookup(macAddress);
        String type=classifier.classify(hostname,services);
        Device d=new Device(ip,hostname,latency,macAddress,vendor,type);
        services.forEach(d::addService);
        return d;
    }
    private String resolve(String ip){
        try{String h=InetAddress.getByName(ip).getCanonicalHostName(); if(h!=null&&!h.isBlank()&&!h.equals(ip)) return h;}catch(Exception ignored){}
        return ip;
    }
    private List<String> hosts(String network,int prefix){
        List<String> out=new ArrayList<>(); long base=NetworkDetector.ipv4ToLong(network); long count=1L<<(32-prefix);
        long first=prefix>=31?base:base+1; long last=prefix>=31?base+count-1:base+count-2;
        for(long v=first;v<=last;v++) out.add(NetworkDetector.longToIpv4(v)); return out;
    }
    private static int compareIp(String a,String b){
        String[] x=a.split("\\."),y=b.split("\\.");
        for(int i=0;i<4;i++){int c=Integer.compare(Integer.parseInt(x[i]),Integer.parseInt(y[i]));if(c!=0)return c;} return 0;
    }
    public interface ScanListener { void progress(int completed,int total); }
}
