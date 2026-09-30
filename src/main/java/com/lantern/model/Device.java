package com.lantern.model;

import com.lantern.agent.AgentClient;
import java.time.LocalDateTime;
import java.util.*;

public class Device {
    private final String ipAddress, hostname, macAddress, vendor, type;
    private final long latencyMs;
    private final LocalDateTime discoveredAt;
    private final List<Service> services = new ArrayList<>();
    private AgentClient.AgentData agentData;
    public Device(String ipAddress,String hostname,long latencyMs,String macAddress,String vendor,String type){this.ipAddress=ipAddress;this.hostname=hostname;this.latencyMs=latencyMs;this.macAddress=macAddress;this.vendor=vendor;this.type=type;this.discoveredAt=LocalDateTime.now();}
    public String getIpAddress(){return ipAddress;} public String getHostname(){return hostname;} public long getLatencyMs(){return latencyMs;} public String getMacAddress(){return macAddress;} public String getVendor(){return vendor;} public String getType(){return type;} public LocalDateTime getDiscoveredAt(){return discoveredAt;}
    public boolean isOnline(){return latencyMs>=0||macAddress!=null||!services.isEmpty()||agentData!=null;}
    public void addService(Service s){if(s!=null&&services.stream().noneMatch(x->x.port()==s.port()))services.add(s);}
    public List<Service> getServices(){return services.stream().sorted(Comparator.comparingInt(Service::port)).toList();}
    public String serviceSignature(){return getServices().stream().map(s->Integer.toString(s.port())).reduce((a,b)->a+","+b).orElse("");}
    public String displayName(){if(agentData!=null&&agentData.hostname()!=null&&!agentData.hostname().isBlank())return agentData.hostname();if(hostname!=null&&!hostname.isBlank()&&!hostname.equals(ipAddress))return hostname;if(vendor!=null&&!vendor.isBlank()&&!vendor.equalsIgnoreCase("Unknown"))return vendor;return ipAddress;}
    public AgentClient.AgentData getAgentData(){return agentData;} public void setAgentData(AgentClient.AgentData data){this.agentData=data;}
}
