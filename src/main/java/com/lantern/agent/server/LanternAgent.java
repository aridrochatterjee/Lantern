package com.lantern.agent.server;

import com.lantern.agent.*;
import com.lantern.agent.admin.*;
import com.lantern.agent.model.SystemSnapshot;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.Base64;

public class LanternAgent {
    public static void main(String[] args) throws Exception {
        AgentConfig cfg = AgentConfig.load(args);
        AdminCollector admin = new AdminCollector(cfg.adminPolicy());
        System.out.println("Lantern Agent v1.1");
        System.out.println("Listening on http://" + cfg.bind() + ":" + cfg.port());
        System.out.println("Token stored in data/agent.token");
        System.out.println("Admin permissions: " + (admin.permissions().isEmpty() ? "none" : String.join(", ", admin.permissions())));
        HttpServer s = HttpServer.create(new InetSocketAddress(cfg.bind(), cfg.port()), 0);
        SystemCollector collector = new SystemCollector();
        s.createContext("/api/v1/health", x -> respond(x, cfg, health(admin)));
        s.createContext("/api/v1/system", x -> respond(x, cfg, json(collector.collect(), admin)));
        s.createContext("/api/v1/resources", x -> respond(x, cfg, json(collector.collect(), admin)));
        s.createContext("/api/v1/network", x -> respond(x, cfg, json(collector.collect(), admin)));
        s.createContext("/api/v1/processes", x -> respond(x, cfg, json(collector.collect(), admin)));
        s.createContext("/api/v1/admin/permissions", x -> respond(x, cfg, permissions(admin)));
        s.createContext("/api/v1/admin/files", x -> files(x, cfg, admin));
        s.createContext("/api/v1/admin/screen", x -> screen(x, cfg, admin));
        s.createContext("/api/v1/admin/clipboard", x -> clipboard(x, cfg, admin));
        s.createContext("/api/v1/admin/browser-history", x -> browserHistory(x, cfg, admin));
        s.setExecutor(Executors.newCachedThreadPool()); s.start();
    }

    private static String health(AdminCollector a) {
        return "{\"agent\":\"lantern\",\"version\":\"1.1.0\",\"status\":\"ok\",\"adminEnabled\":" + (!a.permissions().isEmpty()) + ",\"permissions\":" + arr(a.permissions()) + ",\"timestamp\":" + quote(Instant.now().toString()) + "}";
    }

    private static void files(HttpExchange x, AgentConfig c, AdminCollector a) throws IOException {
        if (!authorized(x, c)) { unauthorized(x); return; }
        try {
            String path = query(x.getRequestURI(), "path");
            String mode = query(x.getRequestURI(), "mode");
            String body = "read".equalsIgnoreCase(mode) ? jsonText(a.readText(path, 1_000_000)) : lines(a.listFiles(path));
            respondAuthorized(x, body, "application/json; charset=utf-8");
        } catch (Exception e) { error(x, 400, e.getMessage()); }
    }

    private static void clipboard(HttpExchange x, AgentConfig c, AdminCollector a) throws IOException {
        if (!authorized(x,c)) { unauthorized(x); return; }
        try { respondAuthorized(x, jsonText(a.clipboardText()), "application/json; charset=utf-8"); }
        catch (Exception e) { error(x, 400, e.getMessage()); }
    }

    private static void browserHistory(HttpExchange x, AgentConfig c, AdminCollector a) throws IOException {
        if (!authorized(x,c)) { unauthorized(x); return; }
        try { respondAuthorized(x, lines(a.browserHistory(100)), "application/json; charset=utf-8"); }
        catch (Exception e) { error(x, 400, e.getMessage()); }
    }

    private static void screen(HttpExchange x, AgentConfig c, AdminCollector a) throws IOException {
        if (!authorized(x, c)) { unauthorized(x); return; }
        try {
            byte[] png = a.screenPng();
            x.getResponseHeaders().set("Content-Type", "image/png"); x.getResponseHeaders().set("Cache-Control", "no-store");
            x.sendResponseHeaders(200, png.length); try (OutputStream o = x.getResponseBody()) { o.write(png); }
        } catch (Exception e) { error(x, 400, e.getMessage()); }
    }

    private static boolean authorized(HttpExchange x, AgentConfig c) { return c.token().equals(x.getRequestHeaders().getFirst("Authorization")); }
    private static void unauthorized(HttpExchange x) throws IOException { x.getResponseHeaders().set("WWW-Authenticate", "Bearer"); x.sendResponseHeaders(401, -1); x.close(); }
    private static void respond(HttpExchange x, AgentConfig c, String body) throws IOException { if (!authorized(x,c)) { unauthorized(x); return; } respondAuthorized(x,body,"application/json; charset=utf-8"); }
    private static void respondAuthorized(HttpExchange x, String body, String type) throws IOException { if (!x.getRequestMethod().equals("GET")) { x.sendResponseHeaders(405,-1); return; } byte[] b=body.getBytes(StandardCharsets.UTF_8); x.getResponseHeaders().set("Content-Type",type); x.getResponseHeaders().set("Cache-Control","no-store"); x.sendResponseHeaders(200,b.length); try(OutputStream o=x.getResponseBody()){o.write(b);} }
    private static void error(HttpExchange x,int code,String msg)throws IOException{String b=jsonText(msg==null?"error":msg);byte[]d=b.getBytes(StandardCharsets.UTF_8);x.getResponseHeaders().set("Content-Type","application/json; charset=utf-8");x.sendResponseHeaders(code,d.length);try(OutputStream o=x.getResponseBody()){o.write(d);}}

    private static String json(SystemSnapshot s, AdminCollector a) { return "{"+q("timestamp",s.timestamp().toString())+","+q("hostname",s.hostname())+","+q("os",s.os())+","+q("kernel",s.kernel())+","+q("architecture",s.architecture())+","+q("hardwareModel",s.hardwareModel())+","+q("cpuModel",s.cpuModel())+","+q("gpu",s.gpu())+","+q("loggedInUser",s.loggedInUser())+","+q("javaVersion",s.javaVersion())+",\"cpuCores\":"+s.cpuCores()+",\"cpuLoadPercent\":"+s.cpuLoadPercent()+",\"memoryTotalBytes\":"+s.memoryTotalBytes()+",\"memoryUsedBytes\":"+s.memoryUsedBytes()+",\"memoryAvailableBytes\":"+s.memoryAvailableBytes()+",\"diskTotalBytes\":"+s.diskTotalBytes()+",\"diskUsedBytes\":"+s.diskUsedBytes()+",\"diskFreeBytes\":"+s.diskFreeBytes()+",\"uptimeSeconds\":"+s.uptimeSeconds()+",\"batteryPresent\":"+s.batteryPresent()+",\"batteryPercent\":"+s.batteryPercent()+",\"networkInterfaces\":"+arr(s.networkInterfaces())+",\"processes\":"+arr(s.processes())+",\"adminEnabled\":"+(!a.permissions().isEmpty())+",\"adminPermissions\":"+arr(a.permissions())+"}"; }
    private static String permissions(AdminCollector a){return "{\"enabled\":"+(!a.permissions().isEmpty())+",\"permissions\":"+arr(a.permissions())+",\"roots\":"+arr(a.policy().allowedRoots().stream().map(Object::toString).toList())+"}";}
    private static String lines(List<String> values){return arr(values);}
    private static String jsonText(String v){return quote(v);}
    private static String q(String k,String v){return "\""+k+"\":"+quote(v);}
    private static String quote(String value){if(value==null)return "null";StringBuilder b=new StringBuilder("\"");for(char c:value.toCharArray()){switch(c){case '\\'->b.append("\\\\");case '"'->b.append("\\\"");case '\n'->b.append("\\n");case '\r'->b.append("\\r");case '\t'->b.append("\\t");case '\b'->b.append("\\b");case '\f'->b.append("\\f");default->{if(c<0x20)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}}return b.append('"').toString();}
    private static String arr(List<String>a){StringBuilder b=new StringBuilder("[");for(int i=0;i<a.size();i++){if(i>0)b.append(',');b.append(quote(a.get(i)));}return b.append(']').toString();}
    private static String query(URI uri,String key){String q=uri.getRawQuery();if(q==null)return null;for(String p:q.split("&")){String[]x=p.split("=",2);if(x.length==2&&x[0].equals(key))try{return URLDecoder.decode(x[1],StandardCharsets.UTF_8);}catch(Exception ignored){}}return null;}
}
