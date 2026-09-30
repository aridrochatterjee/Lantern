package com.lantern.history;

import com.lantern.model.Device;
import java.io.*;import java.nio.file.*;import java.time.*;import java.util.*;

public class HistoryStore {
 private final Path file; public HistoryStore(Path file){this.file=file;}
 public synchronized void saveScan(List<Device> ds){try{if(file.getParent()!=null)Files.createDirectories(file.getParent());boolean e=Files.exists(file);try(BufferedWriter w=Files.newBufferedWriter(file,StandardOpenOption.CREATE,StandardOpenOption.APPEND)){if(!e)w.write("timestamp,ip,hostname,mac,vendor,type,latency,services\n");String now=LocalDateTime.now().toString();for(Device d:ds)w.write(csv(now)+","+csv(d.getIpAddress())+","+csv(d.getHostname())+","+csv(d.getMacAddress())+","+csv(d.getVendor())+","+csv(d.getType())+","+d.getLatencyMs()+","+csv(d.serviceSignature())+"\n");}}catch(IOException ignored){}}
 public List<String> recentLines(int max){if(!Files.exists(file))return List.of();try{List<String> l=Files.readAllLines(file);if(l.size()<=max+1)return l.subList(1,l.size());return l.subList(Math.max(1,l.size()-max),l.size());}catch(IOException e){return List.of();}}
 public Path path(){return file;} private String csv(String s){if(s==null)return "";return "\""+s.replace("\"","\"\"")+"\"";}
}
