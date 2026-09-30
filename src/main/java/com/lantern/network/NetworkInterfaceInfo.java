package com.lantern.network;

import java.util.List;

public class NetworkInterfaceInfo {

    private final String interfaceName;
    private final String localAddress;
    private final String networkAddress;
    private final int prefixLength;
    private final String defaultGateway;
    private final List<String> dnsServers;

    /**
     * Creates network information without gateway or DNS details.
     */
    public NetworkInterfaceInfo(
            String interfaceName,
            String localAddress,
            String networkAddress,
            int prefixLength
    ) {
        this(
                interfaceName,
                localAddress,
                networkAddress,
                prefixLength,
                null,
                List.of()
        );
    }

    /**
     * Creates network information with gateway and DNS details.
     */
    public NetworkInterfaceInfo(
            String interfaceName,
            String localAddress,
            String networkAddress,
            int prefixLength,
            String defaultGateway,
            List<String> dnsServers
    ) {
        this.interfaceName = interfaceName;
        this.localAddress = localAddress;
        this.networkAddress = networkAddress;
        this.prefixLength = prefixLength;
        this.defaultGateway = defaultGateway;

        this.dnsServers = dnsServers == null
                ? List.of()
                : List.copyOf(dnsServers);
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public String getNetworkAddress() {
        return networkAddress;
    }

    public int getPrefixLength() {
        return prefixLength;
    }

    public String getDefaultGateway() {
        return defaultGateway;
    }

    public List<String> getDnsServers() {
        return dnsServers;
    }
}