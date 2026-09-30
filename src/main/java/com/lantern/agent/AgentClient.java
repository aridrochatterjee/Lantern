package com.lantern.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

public class AgentClient {
    public record AgentData(
            String hostname,String os,String kernel,String architecture,String hardwareModel,String cpuModel,String gpu,
            String loggedInUser,String javaVersion,int cpuCores,double cpuLoadPercent,long memoryTotalBytes,long memoryUsedBytes,
            long memoryAvailableBytes,long diskTotalBytes,long diskUsedBytes,long diskFreeBytes,long uptimeSeconds,int batteryPercent,
            List<String> networkInterfaces,List<String> processes,String rawJson,Instant receivedAt,List<String> adminPermissions) {}

    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public AgentData fetch(String host,int port,String token)throws IOException,InterruptedException{
        HttpRequest r=HttpRequest.newBuilder(URI.create("http://"+host+":"+port+"/api/v1/system")).timeout(Duration.ofSeconds(4)).header("Authorization",token).GET().build();
        HttpResponse<String> res=client.send(r,HttpResponse.BodyHandlers.ofString()); if(res.statusCode()!=200) throw new IOException("agent HTTP "+res.statusCode()); String j=res.body();
        return new AgentData(str(j,"hostname"),str(j,"os"),str(j,"kernel"),str(j,"architecture"),str(j,"hardwareModel"),str(j,"cpuModel"),str(j,"gpu"),str(j,"loggedInUser"),str(j,"javaVersion"),(int)lng(j,"cpuCores"),num(j,"cpuLoadPercent"),lng(j,"memoryTotalBytes"),lng(j,"memoryUsedBytes"),lng(j,"memoryAvailableBytes"),lng(j,"diskTotalBytes"),lng(j,"diskUsedBytes"),lng(j,"diskFreeBytes"),lng(j,"uptimeSeconds"),(int)lng(j,"batteryPercent"),array(j,"networkInterfaces"),array(j,"processes"),j,Instant.now(),array(j,"adminPermissions"));
    }
    private static String str(String j,String k){String p="\""+k+"\":\"";int i=j.indexOf(p);if(i<0)return null;i+=p.length();StringBuilder b=new StringBuilder();boolean esc=false;for(;i<j.length();i++){char c=j.charAt(i);if(esc){b.append(switch(c){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';case '"'->'"';case '\\'->'\\';default->c;});esc=false;}else if(c=='\\')esc=true;else if(c=='"')break;else b.append(c);}return b.toString();}
    private static double num(String j,String k){try{int i=j.indexOf("\""+k+"\":")+k.length()+3;int e=i;while(e<j.length()&&"0123456789.-".indexOf(j.charAt(e))>=0)e++;return Double.parseDouble(j.substring(i,e));}catch(Exception e){return -1;}}
    private static long lng(String j,String k){return Math.round(num(j,k));}
    private static List<String> array(String j,String k){int i=j.indexOf("\""+k+"\":[");if(i<0)return List.of();i+=k.length()+4;List<String>out=new ArrayList<>();boolean esc=false;StringBuilder b=new StringBuilder();boolean in=false;for(;i<j.length();i++){char c=j.charAt(i);if(!in){if(c=='"'){in=true;b.setLength(0);}else if(c==']')break;}else if(esc){b.append(switch(c){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';case '"'->'"';case '\\'->'\\';default->c;});esc=false;}else if(c=='\\')esc=true;else if(c=='"'){out.add(b.toString());in=false;}else b.append(c);}return out;}
}
