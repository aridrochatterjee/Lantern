package com.lantern.agent;

import com.lantern.model.Device;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class AgentRegistry {
    public record Target(String ip,int port,String token) {}
    public record ProxyResponse(int status,String contentType,byte[] body) {}
    private final Map<String,Target> targets=new HashMap<>();
    public static AgentRegistry fromArgs(String[] args){AgentRegistry r=new AgentRegistry();for(String a:args)if(a.startsWith("--agent=")){String v=a.substring(8);String[] p=v.split(":",3);if(p.length==3)try{r.targets.put(p[0],new Target(p[0],Integer.parseInt(p[1]),p[2]));}catch(Exception ignored){}}return r;}
    public void enrich(List<Device> devices){AgentClient c=new AgentClient();for(Device d:devices){Target t=targets.get(d.getIpAddress());if(t==null)continue;try{d.setAgentData(c.fetch(t.ip(),t.port(),t.token()));}catch(Exception ignored){}}}
    public boolean hasTargets(){return !targets.isEmpty();}
    public ProxyResponse proxy(String ip,String agentPath,String query) throws IOException,InterruptedException{
        Target t=targets.get(ip); if(t==null) throw new IOException("No paired agent for "+ip);
        String path=agentPath.startsWith("/")?agentPath:"/"+agentPath;
        String url="http://"+t.ip()+":"+t.port()+path+(query==null||query.isBlank()?"":"?"+query);
        HttpRequest req=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Authorization",t.token()).GET().build();
        HttpResponse<byte[]> res=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build().send(req,HttpResponse.BodyHandlers.ofByteArray());
        return new ProxyResponse(res.statusCode(),res.headers().firstValue("Content-Type").orElse("application/octet-stream"),res.body());
    }
}
