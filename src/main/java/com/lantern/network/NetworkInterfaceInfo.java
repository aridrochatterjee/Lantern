package com.lantern.network;

import java.util.List;

public class NetworkInterfaceInfo {
    private final String interfaceName, localAddress, networkAddress, defaultGateway;
    private final int prefixLength;
    private final List<String> dnsServers;
    public NetworkInterfaceInfo(String interfaceName,String localAddress,String networkAddress,int prefixLength){this(interfaceName,localAddress,networkAddress,prefixLength,null,List.of());}
    public NetworkInterfaceInfo(String interfaceName,String localAddress,String networkAddress,int prefixLength,String defaultGateway,List<String> dnsServers){this.interfaceName=interfaceName;this.localAddress=localAddress;this.networkAddress=networkAddress;this.prefixLength=prefixLength;this.defaultGateway=defaultGateway;this.dnsServers=dnsServers==null?List.of():List.copyOf(dnsServers);}
    public String getInterfaceName(){return interfaceName;} public String getLocalAddress(){return localAddress;} public String getNetworkAddress(){return networkAddress;} public int getPrefixLength(){return prefixLength;} public String getDefaultGateway(){return defaultGateway;} public List<String> getDnsServers(){return dnsServers;}
}
